(ns k16.kl.api.proxy
  (:require
   [k16.kl.log :as log]
   [clj-yaml.core :as yaml]
   [k16.kl.api.fs :as api.fs]))

(defn- get-proxies-projection-file [module-name]
  (api.fs/from-config-dir "proxy/" (str module-name ".yaml")))

(defn- route->traefik-rule [{:keys [host path-prefix]}]
  (cond-> (str "HostRegexp(`" host "`)")
    path-prefix (str " && PathPrefix(`" path-prefix "`)")))

(defn- build-routes [module]
  (->> (get-in module [:network :routes])
       (filterv (fn [[_ route]] (get route :enabled true)))
       (reduce (fn [acc [route-name route]]
                 (let [service-name (keyword (:service route))
                       service (get-in module [:network :services service-name])

                       endpoint-name (or (:endpoint route)
                                         (:default-endpoint service))
                       endpoint (get-in service [:endpoints endpoint-name])]

                   (if (and service endpoint)
                     (let [traefik-service-name (str (name service-name)
                                                     "-"
                                                     (name endpoint-name))]
                       (-> acc
                           (assoc-in [:http :routers (name route-name)]
                                     {:rule (route->traefik-rule route)
                                      :service traefik-service-name})
                           (assoc-in [:http :services traefik-service-name :loadbalancer :servers]
                                     [{:url (:url endpoint)}])))
                     (do (when-not service
                           (log/warn (str "Unknown service " service-name)))
                         (when (and endpoint-name
                                    (not endpoint))
                           (log/warn (str "Unknown endpoint " endpoint-name)))
                         acc))))
               {})))

(defn write-proxy-config! [{:keys [module-name module]}]
  (let [routes (build-routes module)
        file (get-proxies-projection-file module-name)]
    (spit file (yaml/generate-string routes))))
