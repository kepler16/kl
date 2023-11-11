(ns k16.kl.api.resolver.downloader
  (:require
   [cli-matic.utils :as cli.util]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.github :as api.github]
   [k16.kl.api.module.parse :as module.parse]
   [k16.kl.log :as log]
   [promesa.core :as p]))

(set! *warn-on-reflection* true)

(defn- relative-to [subpath path]
  (if subpath
    (str/join "/" [subpath path])
    path))

(defn- read-repo-file [identifier sha path]
  (let [res (api.github/request {:path (str "/repos/" identifier "/contents/" path "?ref=" sha)
                                 :headers {"Accept" "application/vnd.github.raw"}})]

    (when (not= 200 (:status res))
      (log/info (str "Failed to pull " identifier "@" sha "/" path))
      (cli.util/exit! (:body res) 1))

    (slurp (:body res))))

(defn- replace-vars [contents vars]
  (->> vars
       (reduce (fn [acc [key value]]
                 (str/replace acc (str "{{" (name key) "}}") value))
               contents)))

(defn download-module-config
  ([module-ref] (download-module-config module-ref {}))
  ([{:keys [url sha subdir] :or {subdir ".kl"}} vars]
   (-> (read-repo-file url sha (relative-to subdir "module.edn"))
       (replace-vars vars)
       edn/read-string
       module.parse/parse-module-data)))

(defn download-remote-module! [{:keys [module-name submodule-name module-ref]}]
  (let [{:keys [sha url subdir]
         :or {subdir ".kl"}} module-ref

        sha-short (subs sha 0 7)

        submodule-dir (-> (api.fs/from-submodule-dir module-name submodule-name)
                          .toString)

        vars {:SHA sha
              :SHA_SHORT sha-short
              :DIR submodule-dir}]

    (log/info (str "Downloading " url "@" sha-short))

    (let [module (download-module-config module-ref vars)]
      @(p/all
        (->> (:include module)
             (map (fn [file]
                    (p/vthread
                     (log/info (str "Downloading " file " [" submodule-name "]"))
                     (let [contents (-> (read-repo-file url sha (relative-to subdir file))
                                        (replace-vars vars))]
                       (spit (api.fs/from-submodule-dir module-name submodule-name file) contents)))))))

      (api.fs/write-edn (api.fs/from-submodule-dir module-name submodule-name "module.edn") module))))
