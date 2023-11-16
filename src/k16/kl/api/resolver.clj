(ns k16.kl.api.resolver
  (:require
   [cli-matic.utils :as cli.util]
   [jsonista.core :as json]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.github :as api.github]
   [k16.kl.api.module.downloader :as module.downloader]
   [k16.kl.api.module.loader :as module.loader]
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
    (log/debug (str "Resolving " url (if subdir (str "/" subdir) ""))))

  (let [sha (if sha sha (get-commit-for-ref url ref))]
    (cond-> {:url url :sha sha :ref ref}
      subdir (assoc :subdir subdir))))

(defn- resolve-module [partial-reference]
  (let [module-ref (resolve-module-ref partial-reference)
        module (module.downloader/download-module-config module-ref)]
    {:ref module-ref
     :module module}))

(defn- resolve-module-tree [{:keys [module lock force-resolve?] :as props}]
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
                     submodules (resolve-module-tree (assoc props :module module))]
                 [submodule-name {:ref ref :module module :submodules submodules}])
               [submodule-name lock-entry])))))
       p/all
       deref
       (into {})))

(defn- flatten-modules-tree [{:keys [tree result]
                              :or {result {}}}]
  (let [[prewalk-result postwalk-modules]
        (->> tree
             (reduce
              (fn [[result modules] [module-name entry]]
                (if-not (contains? result module-name)
                  [(assoc result module-name entry)
                   (conj modules entry)]

                  [result modules]))
              [result []]))]

    (->> postwalk-modules
         (reduce (fn [result entry]
                   (if (:submodules entry)
                     (flatten-modules-tree {:tree (:submodules entry)
                                            :result result})
                     result))
                 prewalk-result))))

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
        (fn [modules [module-name entry]]
          (assoc modules module-name (:ref entry)))
        {})))

(defn- tree->module-diff [tree sub-modules]
  (let [updated
        (->> tree
             (reduce
              (fn [updates [submodule-name entry]]
                (let [{:keys [ref]} entry
                      previous-ref (get-in sub-modules [submodule-name :ref])]
                  (if (not= (:sha ref) (:sha previous-ref))
                    (assoc updates submodule-name (assoc entry :previous-ref previous-ref))
                    updates)))
              {}))

        removed
        (->> sub-modules
             (reduce (fn [removed [submodule-name {:keys [ref]}]]
                       (if-not (contains? tree submodule-name)
                         (assoc removed submodule-name {:ref ref :removed? true})
                         removed))
                     {}))]

    (merge removed updated)))

(defn- resolve-modules [props]
  (let [tree (resolve-module-tree props)
        flattened (flatten-modules-tree {:tree tree})]
    {:lock (tree->lock flattened)
     :modules (tree->modules flattened)
     :module-diff (tree->module-diff flattened (:sub-modules props))}))

(defn pull! [module-name {:keys [update-lockfile?]}]
  (let [module (module.loader/read-module-file (api.fs/get-root-module-file module-name))
        current-lock (api.fs/read-edn (api.fs/get-lock-file module-name))
        current-sub-modules (module.downloader/describe-downloaded-submodules module-name)

        {:keys [lock modules module-diff]}
        (resolve-modules {:module module
                          :lock current-lock
                          :sub-modules current-sub-modules
                          :force-resolve? update-lockfile?})

        lockfile-updated? (not= lock current-lock)]

    (when lockfile-updated?
      (api.fs/write-edn (api.fs/get-lock-file module-name) lock))

    (when (seq module-diff)
      (->> module-diff
           (map (fn [[submodule-name {:keys [ref removed?]}]]
                  (p/vthread
                   (if removed?
                     (module.downloader/rm-local-module!
                      {:module-name module-name
                       :submodule-name (name submodule-name)})

                     (module.downloader/download-remote-module!
                      {:module-name module-name
                       :submodule-name (name submodule-name)
                       :module-ref ref})))))

           p/all
           deref)

      (println)
      (doseq [[submodule-name {:keys [ref previous-ref removed?]}] module-diff]
        (let [module-name (str "@|yellow " (name submodule-name) "|@@|white @|@")
              from (when previous-ref (subs (:sha previous-ref) 0 7))
              to (subs (:sha ref) 0 7)]
          (cond
            from
            (log/info (str "@|bold,cyan ~|@ " module-name "@|bold,red " from "|@" "@|bold,green " to "|@"))

            removed?
            (log/info (str "@|bold,red -|@ " module-name "@|bold,red " to "|@"))

            :else
            (log/info (str "@|bold,green +|@ " module-name "@|bold,green " to "|@")))))

      (println))

    {:modules modules
     :lockfile-updated? lockfile-updated?}))
