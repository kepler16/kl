(ns kl.commands.proxy
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clj-yaml.core :as yaml]
   [jansi-clj.core :as color]
   [clojure.string :as str]))

(defn get-proxies-file []
  (let [file (io/file (System/getProperty "user.home") ".config/kl/proxies.edn")]
    (io/make-parents file)
    file))

(defn read-proxies-file [^java.io.File file]
  (try
    (edn/read-string (slurp file))
    (catch Exception _ {})))

(defn write-proxies-file [^java.io.File file proxies]
  (spit file (prn-str proxies)))

(defn get-proxies-projection-file []
  (let [file (io/file (System/getProperty "user.home") ".config/kl/proxy/managed.yaml")]
    (io/make-parents file)
    file))

(defn proxy->routing-rule [{:keys [domain prefix]}]
  (cond-> (str "Host(`" domain "`)")
    prefix (str " && PathPrefix(`" prefix "`)")))

(defn project-proxies [proxies]
  (let [file (get-proxies-projection-file)

        traefik-config
        (->> proxies
             (reduce (fn [acc [name proxy]]
                       (let [{:keys [url enabled]} proxy]
                         (if-not enabled
                           acc
                           (-> acc
                               (assoc-in [:http :routers name]
                                         {:rule (proxy->routing-rule proxy)
                                          :service name})
                               (assoc-in [:http :services name :loadbalancer :servers]
                                         [{:url url}])))))
                     {}))

        data (yaml/generate-string traefik-config)]
    (spit file data)))

(defn calculate-table-widths [proxies]
  (let [init {:name 0 :url 0 :domain 0 :prefix 0}]
    (->> proxies
         (reduce (fn [acc [name proxy]]
                   (let [{:keys [url domain prefix]} proxy
                         namec (count name)
                         urlc (count url)
                         prefixc (count (or prefix ""))
                         domainc (count domain)]
                     (cond-> acc
                       (> namec (:name acc)) (assoc :name namec)
                       (> urlc (:url acc)) (assoc :url urlc)
                       (> prefixc (:prefix acc)) (assoc :prefix prefixc)
                       (> domainc (:domain acc)) (assoc :domain domainc))))
                 init))))

(defn format-string [s n]
  (let [ellipses ".."]
    (cond
      (< (count s) n) (str s (apply str (repeat (- n (count s)) " ")))
      (> (count s) n) (str (subs s 0 (- n (count ellipses))) ellipses)
      :else s)))

(defn list-proxies
  ([_] (list-proxies))
  ([]
   (let [proxies (read-proxies-file (get-proxies-file))

         widths (-> (calculate-table-widths proxies)
                    (merge {:enabled 7}))]

     (doseq [col [:name :domain :url :prefix :enabled]]
       (let [width (get widths col)
             value (-> (name col)
                       str/capitalize
                       (format-string width))]
         (print (color/render (str "@|bold,yellow " value "|@ ")))))

     (println)

     (doseq [[name proxy] proxies]
       (let [name (-> name (format-string (:name widths)))
             domain (-> proxy :domain (format-string (:domain widths)))
             url (-> proxy :url (format-string (:url widths)))
             prefix (-> proxy :prefix (format-string (:prefix widths)))
             enabled (-> proxy :enabled str (format-string (:enabled widths)))

             line (str
                   "@|bold,blue " name "|@ "
                   domain " "
                   url " "
                   prefix " "
                   "@|bold," (if (:enabled proxy) "green" "red") " " enabled "|@ "
                   \newline)]
         (print (color/render line))))

     (println))))

(defn normalize-url [url]
  (let [pattern #"^((?:.*:\/\/)?[^:\/]*)(?::(\d+))?$"
        [_ host port] (re-matches pattern url)
        host (if (seq host) host "http://host.docker.internal")]

    (str host ":" port)))

(defn create-proxy [{:keys [opts]}]
  (let [file (get-proxies-file)
        proxies (read-proxies-file file)
        url (-> opts :url normalize-url)

        proxies' (assoc proxies
                        (:name opts)
                        {:domain (:domain opts)
                         :prefix (:prefix opts)
                         :url url
                         :enabled true})]
    (write-proxies-file file proxies')
    (project-proxies proxies')
    (list-proxies)))

(defn delete-proxy [{:keys [opts]}]
  (let [file (get-proxies-file)
        proxies (read-proxies-file file)
        proxies' (dissoc proxies (:name opts))]
    (write-proxies-file file proxies')
    (project-proxies proxies')
    (list-proxies)))

(defn change-proxy-status [names status]
  (let [file (get-proxies-file)
        proxies (read-proxies-file file)
        proxies' (->> (str/split names #",")
                      (filter seq)
                      (reduce (fn [proxies name]
                                (assoc-in proxies [name :enabled] status))
                              proxies))]
    (write-proxies-file file proxies')
    (project-proxies proxies')
    (list-proxies)))

(defn enable-proxy [{:keys [opts]}]
  (change-proxy-status (:name opts) true))

(defn disable-proxy [{:keys [opts]}]
  (change-proxy-status (:name opts) false))

(def commands [{:cmds ["proxy" "list"] :fn list-proxies}
               {:cmds ["proxy" "create"]
                :fn create-proxy
                :require [:name :domain :url]
                :coerce {:url :string
                         :prefix :string}
                :exec-args {:host "http://host.docker.internal"}}
               {:cmds ["proxy" "delete"] :fn delete-proxy :args->opts [:name] :require [:name]}
               {:cmds ["proxy" "enable"] :fn enable-proxy :args->opts [:name] :require [:name]}
               {:cmds ["proxy" "disable"] :fn disable-proxy :args->opts [:name] :require [:name]}])
