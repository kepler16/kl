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

(defn- resolve-module-sha [{:keys [url sha ref subdir]
                            :or {ref "master"}}]

  (when-not sha
    (log/info (str "Resolving " url (if subdir (str "/" subdir) ""))))

  (let [sha (if sha sha (get-commit-for-ref url ref))]
    (cond-> {:url url :sha sha :ref ref}
      subdir (assoc :subdir subdir))))

(defn- resolve-modules [{:keys [module lock force-resolve?]}]
  (->> (:modules module)
       (map (fn [[submodule-name location]]
              (p/vthread
               (let [lock-entry (get lock submodule-name)

                     should-resolve?
                     (or (not (:sha lock-entry))

                         (and (:sha location) (not= (:sha location) (:sha lock-entry)))
                         (and (:ref location) (not= (:ref location) (:ref lock-entry)))
                         (and (:subdir location) (not= (:subdir location) (:subdir lock-entry)))

                         force-resolve?)]
                 (if should-resolve?
                   [submodule-name (resolve-module-sha location)]
                   [submodule-name lock-entry])))))
       doall
       (map (fn [promise] @promise))
       (into {})))

(defn pull! [module-name {:keys [update-lockfile? force?]}]
  (let [module (api.fs/read-edn (api.fs/get-root-module-file module-name))
        lock (api.fs/read-edn (api.fs/get-lock-file module-name))

        modules (resolve-modules {:module module
                                  :lock lock
                                  :force-resolve? update-lockfile?})

        lockfile-updated? (not= modules lock)

        downloads (when (or lockfile-updated? force?)
                    (->> modules
                         (map (fn [[submodule-name module]]
                                (p/vthread
                                 (resolver.downloader/download-remote-module!
                                  {:module-name module-name
                                   :submodule-name (name submodule-name)
                                   :module module}))))

                         doall))]

    (when lockfile-updated?
      (api.fs/write-edn (api.fs/get-lock-file module-name) modules))

    (when downloads
      (doseq [download downloads]
        @download))

    {:modules modules
     :lockfile-updated? lockfile-updated?}))
