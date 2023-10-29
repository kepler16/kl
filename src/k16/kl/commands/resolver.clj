(ns k16.kl.commands.resolver
  (:require
   [clojure.string :as str]
   [k16.kl.log :as log]))

(defn- setup-macos-resolver []
  (log/info "sudo mkdir -p /etc/resolver\necho 'nameserver 127.0.0.1' | sudo tee -a /etc/resolver/test > /dev/null"))

(defn- setup-linux-resolver []
  (log/info "(echo 'nameserver 127.0.0.1'; sudo cat /etc/resolv.conf) | sudo tee -a /etc/resolv.conf > /dev/null"))

(defn- setup-resolver! [_]
  (let [os (-> (System/getProperty "os.name") .toLowerCase)]
    (cond
      (str/includes? os "mac") (setup-macos-resolver)
      (str/includes? os "linux") (setup-linux-resolver)
      :else (throw (ex-info "platform not supported" {:platform os})))))

(def cmd
  {:command "resolver"
   :description "Manage core network components"

   :subcommands [{:command "setup"
                  :description "Produce bash script for configuring the system resolver"
                  :runs setup-resolver!}]})
