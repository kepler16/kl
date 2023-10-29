(ns k16.kl.api.proxy
  (:require
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [meta-merge.core :as metamerge]
   [k16.kdev.api.fs :as api.fs]))

(defn- get-proxies-projection-file [group-name]
  (api.fs/from-config-dir "proxy/" group-name ".yaml"))

(defn- route->traefik-rule [{:keys [host path-prefix]}]
  (cond-> (str "Host(`" host "`)")
    path-prefix (str " && PathPrefix(`" path-prefix "`)")))

(defn- build-routes [module]
  (let [services
        (->> (get-in module [:network :services])
             (reduce (fn [acc [service-name service-def]]
                       (->> (:endpoints service-def)
                            (reduce (fn [acc [endpoint-name endpoint]]
                                      (let [full-name (str (name service-name) "-" (name endpoint-name))]
                                        (assoc-in acc [:http :services full-name :loadbalancer :servers]
                                                  [{:url (:url endpoint)}])))
                                    acc)))

                     {}))

        routes
        (->> (get-in module [:network :routes])
             (filter (fn [[_ route]] (get route :enabled true)))
             (reduce (fn [acc [route-name route]]
                       (let [service-name (keyword (:service route))
                             service (get-in module [:network :services service-name])

                             endpoint-name (or (:endpoint route)
                                               (:default-endpoint service))]

                         (assoc-in acc [:http :routers (name route-name)]
                                   {:rule (route->traefik-rule route)
                                    :service (str (name service-name) "-" (name endpoint-name))})))
                     {}))]

    (metamerge/meta-merge services routes)))

(defn write-proxy-config! [{:keys [group-name module]}]
  (let [routes (build-routes module)
        file (get-proxies-projection-file group-name)]

    (io/make-parents file)
    (spit file (yaml/generate-string routes))))
