(ns kl.commands.network
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn gen-hash [n]
  (->> (repeatedly n #(rand-int 256))
       (map #(format "%02x" %))
       (apply str)))

(defn prepare-config []
  (let [dir (io/file (System/getProperty "java.io.tmpdir") (str "kl-network" (gen-hash 5)))
        files ["network.yml" "config/dnsmasq-internal.conf" "config/dnsmasq-external.conf"]]

    (->> files
         (map (fn [file]
                (let [source (io/resource file)
                      sink (io/file dir file)]
                  (io/make-parents sink)
                  (spit sink (slurp source)))))
         dorun)

    dir))

(defn start-network [_]
  (let [dir (prepare-config)]

    (println "Starting network")

    (shell/sh "docker" "compose"
              "-p" "kl"
              "-f" (.toString (io/file dir "network.yml"))
              "up" "-d" "--remove-orphans")

    (println "Network started")))

(defn stop-network [_]
  (let [dir (prepare-config)]

    (println "Stopping network")

    (shell/sh "docker" "compose"
              "-p" "kl"
              "-f" (.toString (io/file dir "network.yml"))
              "stop")

    (println "Network stopped")))

(def commands [{:cmds ["up"] :fn start-network}
               {:cmds ["down"] :fn stop-network}])
