(ns k16.kl.commands.routes
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.log :as log]
   [k16.kl.prompt.config :as prompt.config]))

(defn- get-tree-branch [i count]
  (cond
    (= (- count 1) i) "└──"
    (= 0 i) "├──"
    :else "├──"))

(defn- list-routes-tree [routes]
  (let [routes-by-host (->> routes
                            (group-by :host)
                            (map-indexed list))]

    (log/info "@|bold routes|@")

    (doseq [[i [host routes]] routes-by-host]
      (log/info (str (get-tree-branch i (count routes-by-host)) " @|yellow " host "|@"))
      (doseq [[j route] (map-indexed list routes)]
        (let [border (if (= i (- (count routes-by-host) 1))
                       " "
                       "│")
              branch (str border "   " (get-tree-branch j (count routes)))
              path (str " @|bold,green " (or (:path-prefix route) "/") "|@")
              target-name (str "@|white [" (-> route :service name) "@" (-> route :endpoint-name name) "]|@")
              target (str " -> @|green " (get-in route [:endpoint :url]) "|@ " target-name)]
          (log/info (str branch path target)))))))

(defn- list-routes-table [routes]
  (pprint/print-table [:name :host :path-prefix :service :endpoint :url]
                      (->> routes
                           (map (fn [route]
                                  (-> route
                                      (assoc :endpoint (:endpoint-name route))
                                      (assoc :url (-> route :endpoint :url))))))))

(defn- list-routes [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        routes
        (->> (get-in module [:network :routes])
             (map (fn [[route-name route]]
                    (let [service (get-in module [:network :services (:service route)])
                          endpoint-name (or (:endpoint route)
                                            (:default-endpoint service))
                          endpoint (get-in service [:endpoints endpoint-name])]
                      (merge route
                             {:name (name route-name)
                              :path-prefix (or (:path-prefix route) "/")
                              :endpoint-name endpoint-name
                              :endpoint endpoint
                              :url (or (:endpoint route)
                                       (:default-endpoint service))
                              :enabled (get route :enabled true)}))))
             (sort (fn [a b]
                     (let [a (count (:path-prefix a))
                           b (count (:path-prefix b))]
                       (cond
                         (> a b) 1
                         (< a b) -1
                         :else 0)))))]
    (case (:output props)
      "tree" (list-routes-tree routes)
      "table" (list-routes-table routes))))

(defn- apply-routes! [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)]

    (api.proxy/write-proxy-config! {:module-name module-name
                                    :module module})

    (log/info "Routes applied")))

(def cmd
  {:command "routes"
   :description "Manage network routes"

   :subcommands [{:command "list"
                  :description "List all routes"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}

                         {:option "output"
                          :short "o"
                          :default "table"
                          :type :string}]

                  :runs list-routes}

                 {:command "apply"
                  :description "Apply any route changes"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}]

                  :runs apply-routes!}]})
