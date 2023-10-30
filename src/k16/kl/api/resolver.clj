(ns k16.kl.api.resolver
  (:require
   [cli-matic.utils :as cli.util]
   [jsonista.core :as json]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.github :as api.github]
   [k16.kl.api.resolver.downloader :as resolver.downloader]
   [k16.kl.log :as log]
   [promesa.core :as p]))

(set! *warn-on-reflection* true)

(defn- get-commit-for-ref [identifier ref]
  (let [res (api.github/request {:path (str "/repos/" identifier "/commits/" ref)})
        data (-> res
                 :body
                 (json/read-value json/keyword-keys-object-mapper))]

    (when (not= 200 (:status res))
      (log/info (str "Failed to resolve " identifier "@" ref))
      (cli.util/exit! (:message data) 1))

    (:sha data)))

(defn- resolve-module-ref [{:keys [url sha ref subdir]}]
  (when-not sha
    (log/info (str "Resolving " url (if subdir (str "/" subdir) ""))))

  (let [sha (if sha sha (get-commit-for-ref url ref))]
    (cond-> {:url url :sha sha :ref ref}
      subdir (assoc :subdir subdir))))

(defn- resolve-module [partial-reference]
  (let [module-ref (resolve-module-ref partial-reference)
        module (resolver.downloader/download-module-config module-ref)]
    {:ref module-ref
     :module module}))

(defn- resolve-modules-tree [{:keys [module lock force-resolve?] :as props}]
  (->> (:modules module)
       (map
        (fn [[submodule-name partial-ref]]
          (p/vthread
           (let [lock-entry (get lock submodule-name)
                 current-reference (:ref lock-entry)

                 ref (when (and (not (:sha partial-ref))
                                (not (:ref partial-ref)))
                       "master")
                 partial-ref (cond-> partial-ref
                               ref (assoc :ref ref))

                 should-resolve?
                 (or (not (:sha current-reference))

                     (and (:sha partial-ref) (not= (:sha partial-ref) (:sha current-reference)))
                     (and (:ref partial-ref) (not= (:ref partial-ref) (:ref current-reference)))
                     (and (:subdir partial-ref) (not= (:subdir partial-ref) (:subdir current-reference)))

                     force-resolve?)]

             (if should-resolve?
               (let [{:keys [module ref]} (resolve-module partial-ref)
                     submodules (resolve-modules-tree (assoc props :module module))]
                 [submodule-name {:ref ref :module module :submodules submodules}])
               [submodule-name lock-entry])))))
       p/all
       deref
       (into {})))

(defn- deduplicate-tree [{:keys [tree result]
                          :or {result {}}}]
  (->> tree
       (reduce
        (fn [result [module-name entry]]
          (if-not (contains? result module-name)
            (deduplicate-tree
             {:tree (:submodules entry)
              :result (assoc result module-name entry)})
            result))
        result)))

(defn- tree->lock [tree]
  (->> tree
       (reduce
        (fn [lock [module-name entry]]
          (assoc lock module-name
                 {:ref (:ref entry)
                  :submodules (tree->lock (:submodules entry))}))
        {})))

(defn- tree->modules [tree]
  (->> tree
       (reduce
        (fn [lock [module-name entry]]
          (assoc lock module-name (:ref entry)))
        {})))

(defn- resolve-modules [props]
  (let [tree (resolve-modules-tree props)
        tree' (deduplicate-tree {:tree tree})]
    {:lock (tree->lock tree')
     :modules (tree->modules tree')}))

(defn pull! [module-name {:keys [update-lockfile? force?]}]
  (let [module (api.fs/read-edn (api.fs/get-root-module-file module-name))
        current-lock (api.fs/read-edn (api.fs/get-lock-file module-name))

        {:keys [lock modules]}
        (resolve-modules {:module module
                          :lock current-lock
                          :force-resolve? update-lockfile?})

        lockfile-updated? (not= lock current-lock)]

    (when lockfile-updated?
      (api.fs/write-edn (api.fs/get-lock-file module-name) lock))

    (when (or lockfile-updated? force?)
      (->> modules
           (map (fn [[submodule-name module-ref]]
                  (p/vthread
                   (resolver.downloader/download-remote-module!
                    {:module-name module-name
                     :submodule-name (name submodule-name)
                     :module-ref module-ref}))))

           p/all
           deref))

    {:modules modules
     :lockfile-updated? lockfile-updated?}))
