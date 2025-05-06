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
       (mapv #(format "%02x" %))
       (apply str)))

(defn- get-tmp-dir []
  (io/file (System/getProperty "java.io.tmpdir") (str "kl-network-" (gen-hash 5))))

(defn- write-network-module []
  (let [prefix ".kl/network"
        module-file (io/resource "k16/kl/module/network/module.edn")
        module-raw (-> module-file
                       slurp
                       (str/replace "{{DIR}}" (.toString (api.fs/from-config-dir prefix))))
        module (edn/read-string module-raw)]

    (spit (api.fs/from-config-dir prefix "module.edn") module-raw)

    (doseq [file (:include module)]
      (let [contents (slurp (io/resource (str "k16/kl/module/network/" file)))]
        (spit (api.fs/from-config-dir prefix file) contents)))

    module))

(def ^:private os
  (-> (System/getProperty "os.name") .toLowerCase))

(def ^:private default-hosts
  (cond
    (str/includes? os "linux") ["host.docker.internal:172.17.0.1"]
    :else []))

(defn- start-network! [{:keys [host-dns host-dns-port add-host]}]
  (let [workdir (api.fs/from-config-dir ".kl/network")

        extra-hosts (->> (into default-hosts add-host)
                         (reduce (fn [acc host]
                                   (let [[host ip] (str/split host #":")]
                                     (assoc acc host ip)))
                                 {})
                         (filterv (fn [[_ ip]]
                                    (and ip (not= "" ip))))
                         (mapv (fn [[host ip]] (str host ":" ip))))

        module (cond-> (write-network-module)
                 (not host-dns)
                 (assoc-in [:containers :dnsmasq-external :enabled]
                           false)

                 host-dns-port
                 (assoc-in [:containers :dnsmasq-external :ports]
                           [(str host-dns-port ":53/udp") (str host-dns-port ":53/tcp")])

                 (seq extra-hosts)
                 (assoc-in [:containers :proxy :extra_hosts]
                           extra-hosts))]

    (api.proxy/write-proxy-config! {:module-name "kl"
                                    :module module})
    (api.executor/run-module-containers! {:module module
                                          :direction :up
                                          :project-name "kl"
                                          :workdir workdir})))

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

(def ^:private default-port
  (cond
    (str/includes? os "mac") "53"
    (str/includes? os "linux") "5343"
    :else "5343"))

(def cmd
  {:command "network"
   :description "Manage core network components"

   :subcommands [{:command "start"
                  :description "Start the core network components"

                  :opts [{:option "host-dns"
                          :default true
                          :type :with-flag}
                         {:option "host-dns-port"
                          :default default-port
                          :type :string}
                         {:option "add-host"
                          :multiple true
                          :type :string}]

                  :runs start-network!}

                 {:command "stop"
                  :description "Stop the core network components"
                  :runs stop-network!}]})
