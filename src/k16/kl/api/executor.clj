(ns k16.kl.api.executor
  (:require
   [babashka.process :as proc]
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [k16.kl.api.fs :as api.fs]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

(defn- build-docker-compose [module]
  (let [base (cond-> {:networks {:kl {:external true}}}
               (:volumes module) (assoc :volumes (:volumes module))
               (:networks module) (assoc :networks (:networks module)))

        containers
        (->> (:containers module)
             (filter (fn [[_ container]]
                       (get container :enabled true)))
             (map (fn [[container-name container]]
                    [container-name
                     (metamerge/meta-merge {:networks {:kl {}}
                                            :dns "172.5.0.100"}
                                           (dissoc container :enabled))]))

             (into {}))]

    (cond-> base
      (seq containers) (assoc :services containers))))

(defn run-module-containers! [{:keys [module direction project-name workdir]}]
  (let [compose-data (build-docker-compose module)
        compose-file (io/file workdir "docker-compose.yaml")

        direction (if (:services compose-data) direction :down)

        args (case direction
               :up ["-f" (.toString compose-file) "up" "-d" "--remove-orphans"]
               :down ["down"])]

    (io/make-parents compose-file)
    (spit compose-file (yaml/generate-string compose-data))

    (try
      (proc/shell (concat
                   ["docker" "compose"
                    "--project-name" project-name]

                   args))
      (catch Exception _))))

(defn run-module! [{:keys [module-name module direction]}]
  (run-module-containers! {:module module
                           :direction direction
                           :project-name (str "kl-" module-name)
                           :workdir (api.fs/from-module-work-dir module-name)}))
