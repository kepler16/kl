(ns k16.kl.commands.routes
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.log :as log]
   [k16.kl.prompt.config :as prompt.config]))

(defn- get-tree-branch [i count]
  (cond
    (= (- count 1) i) "└──"
    (= 0 i) "├──"
    :else "├──"))

(defn- list-routes-tree [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        state (api.state/get-state module-name)

        routes (->> (get-in module [:network :routes])
                    (map (fn [[route-name route]]
                           (merge route
                                  {:name (name route-name)
                                   :path-prefix (or (:path-prefix route) "/")
                                   :enabled (get-in state [:network :routes route-name :enabled] true)})))
                    (sort (fn [a b]
                            (let [a (count (:path-prefix a))
                                  b (count (:path-prefix b))]
                              (cond
                                (> a b) 1
                                (< a b) -1
                                :else 0)))))

        routes-by-host (->> routes
                            (group-by :host)
                            (map-indexed list))]

    (log/info (str "@|bold routes|@"))

    (doseq [[i [host routes]] routes-by-host]
      (log/info (str (get-tree-branch i (count routes-by-host)) " @|yellow " host "|@"))
      (doseq [[j route] (map-indexed list routes)]
        (let [border (if (= i (- (count routes-by-host) 1))
                       " "
                       "│")

              branch (str border "   " (get-tree-branch j (count routes)))

              path (str " @|bold,green " (or (:path-prefix route) "/") "|@")

              service (get-in module [:network :services (:service route)])
              endpoint (name (or (:endpoint route)
                                 (:default-endpoint service)))

              target (str " -> " (name (:service route)) "@|white @|@@|cyan " endpoint "|@")]
          (log/info (str branch path target)))))))

(defn- list-routes-table [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        state (api.state/get-state module-name)

        routes (->> (get-in module [:network :routes])
                    (map (fn [[route-name route]]
                           (let [service (get-in module [:network :services (:service route)])]
                             (merge route
                                    {:name (name route-name)
                                     :endpoint (or (:endpoint route)
                                                   (:default-endpoint service))
                                     :enabled (get-in state [:network :routes route-name :enabled] true)})))))]

    (pprint/print-table [:name :host :path-prefix :service :endpoint :enabled] routes)))

(defn- list-routes [props]
  (case (:output props)
    "tree" (list-routes-tree props)
    "table" (list-routes-table props)))

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
                          :short 0
                          :type :string}

                         {:option "output"
                          :short "o"
                          :default "table"
                          :type :string}]

                  :runs list-routes}

                 {:command "apply"
                  :description "Apply any route changes"

                  :opts [{:option "module"
                          :short 0
                          :type :string}]

                  :runs apply-routes!}

                 {:command "configure"
                  :description "Select which routes are enabled or disabled"

                  :opts [{:option "module"
                          :short 0
                          :type :string}]

                  :runs (fn [_])}

                 {:command "set-service"
                  :description "Set the service for a route"

                  :opts [{:option "module"
                          :short 0
                          :type :string}]

                  :runs (fn [_])} {:command "set-endpoint"
                                   :description "Set the endpoint for a route"

                                   :opts [{:option "module"
                                           :short 0
                                           :type :string}]

                                   :runs (fn [_])}]})
