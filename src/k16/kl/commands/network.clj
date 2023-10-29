(ns k16.kl.commands.network
  (:require
   [babashka.process :as proc]
   [clojure.java.io :as io]))

(defn- gen-hash [n]
  (->> (repeatedly n #(rand-int 256))
       (map #(format "%02x" %))
       (apply str)))

(defn- rm-dir [^java.io.File file]
  (when (.isDirectory file)
    (run! rm-dir (.listFiles file)))
  (io/delete-file file))

(defn- prepare-config []
  (let [dir (io/file (System/getProperty "java.io.tmpdir") (str "kl-network" (gen-hash 5)))
        files ["config/dnsmasq-internal.conf" "config/dnsmasq-external.conf"
               "images/Dockerfile.dnsmasq-external" "images/Dockerfile.dnsmasq-internal"
               "network.yml"]]

    (->> files
         (map (fn [file]
                (let [source (io/resource file)
                      sink (io/file dir file)]
                  (io/make-parents sink)
                  (spit sink (slurp source)))))
         dorun)

    dir))

(defn- start-network! [_]
  (let [dir (prepare-config)]

    (println "Starting network")

    (proc/shell ["docker" "compose"
                 "-p" "kl"
                 "-f" (.toString (io/file dir "network.yml"))
                 "up" "-d" "--remove-orphans"])

    (rm-dir dir)

    (println "Network started")))

(defn- stop-network! [_]
  (let [dir (prepare-config)]

    (println "Stopping network")

    (proc/shell ["docker" "compose"
                 "-p" "kl"
                 "-f" (.toString (io/file dir "network.yml"))
                 "stop"])

    (rm-dir dir)

    (println "Network stopped")))

(def cmd
  {:command "network"
   :description "Manage core network components"

   :subcommands [{:command "start"
                  :description "Start the core network components"
                  :runs start-network!}

                 {:command "stop"
                  :description "Stop the core network components"
                  :runs stop-network!}]})
