(ns k16.kl.api.module.downloader
  (:require
   [cli-matic.utils :as cli.util]
   [clojure.string :as str]
   [jsonista.core :as json]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.github :as api.github]
   [k16.kl.api.module.loader :as module.loader]
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
      (log/error (str "Failed to pull " identifier "@" sha "/" path))
      (cli.util/exit! (:body res) 1))

    (slurp (:body res))))

(defn- list-repo-files [identifier sha path]
  (let [res (api.github/request {:path (str "/repos/" identifier "/contents/" path "?ref=" sha)
                                 :headers {"Accept" "application/vnd.github.raw"}})]

    (when (not= 200 (:status res))
      (log/error (str "Failed to list files in " identifier "@" sha "/" path))
      (cli.util/exit! (:body res) 1))

    (json/read-value (:body res) json/keyword-keys-object-mapper)))

(defn- replace-vars [vars contents]
  (->> vars
       (reduce (fn [acc [key value]]
                 (str/replace acc (str "{{" (name key) "}}") value))
               contents)))

(defn download-module-config
  ([module-ref] (download-module-config module-ref {}))
  ([{:keys [url sha subdir] :or {subdir ".kl"}} vars]
   (let [file-name (->> (list-repo-files url sha subdir)
                        (map (fn [file]
                               (-> (:path file)
                                   (str/split #"\/")
                                   last)))
                        (filter (fn [file]
                                  (re-matches #"module\.(edn|yaml|yml|json)" file)))
                        first)]

     (->> (read-repo-file url sha (relative-to subdir file-name))
          (replace-vars vars)
          (module.loader/parse-module-contents file-name)))))

(defn download-remote-module! [{:keys [module-name submodule-name module-ref]}]
  (let [{:keys [sha url subdir]
         :or {subdir ".kl"}} module-ref

        sha-short (subs sha 0 7)

        submodule-dir (-> (api.fs/from-submodule-dir module-name submodule-name)
                          .toString)

        vars {:SHA sha
              :SHA_SHORT sha-short
              :DIR submodule-dir}]

    (log/debug (str "Pulling module " url "@@|cyan " sha-short "|@"))

    (let [module (download-module-config module-ref vars)]
      @(p/all
        (->> (:include module)
             (map (fn [file]
                    (p/vthread
                     (log/debug (str "Downloading " file " from " submodule-name))
                     (let [contents (->> (read-repo-file url sha (relative-to subdir file))
                                         (replace-vars vars))]
                       (spit (api.fs/from-submodule-dir module-name submodule-name file) contents)))))))

      (api.fs/write-edn (api.fs/from-submodule-dir module-name submodule-name "module.edn") module))))
