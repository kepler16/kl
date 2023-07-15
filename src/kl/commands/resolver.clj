(ns kl.commands.resolver
  (:require
   [clojure.string :as str]))

(defn setup-macos-resolver []
  (println "sudo mkdir -p /etc/resolver\necho 'nameserver 127.0.0.1' | sudo tee -a /etc/resolver/test > /dev/null"))

(defn setup-linux-resolver []
  (println "(echo 'nameserver 127.0.0.1'; sudo cat /etc/resolv.conf) | sudo tee -a /etc/resolv.conf > /dev/null"))

(defn setup-resolver [_]
  (let [os (-> (System/getProperty "os.name") .toLowerCase)]
    (cond
      (str/includes? os "mac") (setup-macos-resolver)
      (str/includes? os "linux") (setup-linux-resolver)
      :else (throw (ex-info "platform not supported" {:platform os})))))

(def commands [{:cmds ["resolver" "setup"]
                :fn setup-resolver
                :args->opts [:lib]}])
