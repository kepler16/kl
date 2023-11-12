(ns k16.kl.commands.network
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [k16.kl.api.executor :as api.executor]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.proxy :as api.proxy]))

(defn- gen-hash [n]
  (->> (repeatedly n #(rand-int 256))
       (map #(format "%02x" %))
       (apply str)))

(defn- get-tmp-dir []
  (io/file (System/getProperty "java.io.tmpdir") (str "kl-network-" (gen-hash 5))))

(defn- write-network-module []
  (let [prefix ".kl/network"
        module-file (io/resource "network-module/module.edn")
        module-raw (-> module-file
                       slurp
                       (str/replace "{{DIR}}" (.toString (api.fs/from-config-dir prefix))))
        module (edn/read-string module-raw)]

    (spit (api.fs/from-config-dir prefix "module.edn") module-raw)

    (doseq [file (:include module)]
      (let [contents (slurp (io/resource (str "network-module/" file)))]
        (spit (api.fs/from-config-dir prefix file) contents)))

    module))

(defn- start-network! [_]
  (let [workdir (get-tmp-dir)
        module (write-network-module)]
    (api.proxy/write-proxy-config! {:module-name "kl"
                                    :module module})
    (api.executor/run-module-containers! {:module module
                                          :direction :up
                                          :project-name "kl"
                                          :workdir workdir})
    (api.fs/rm-dir! workdir)))

(defn- stop-network! [_]
  (let [workdir (get-tmp-dir)
        module (write-network-module)]
    (api.proxy/write-proxy-config! {:module-name "kl"
                                    :module {}})
    (api.executor/run-module-containers! {:module module
                                          :direction :down
                                          :project-name "kl"
                                          :workdir workdir})
    (api.fs/rm-dir! workdir)))

(def cmd
  {:command "network"
   :description "Manage core network components"

   :subcommands [{:command "start"
                  :description "Start the core network components"
                  :runs start-network!}

                 {:command "stop"
                  :description "Stop the core network components"
                  :runs stop-network!}]})
