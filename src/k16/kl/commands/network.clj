(ns k16.kl.commands.network
  (:require
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt.config :as prompt.config]
   [clojure.pprint :as pprint]
   [meta-merge.core :as metamerge]
   [pretty.cli.prompt :as prompt]))

(defn- set-default-service-endpoint! [props]
  (let [group-name (prompt.config/get-group-name props)

        {:keys [modules]} (api.resolver/pull! group-name {})
        module (api.module/get-resolved-module group-name modules)

        state (api.state/get-state group-name)

        service-name
        (-> (prompt/list-select "Select Service"
                                (->> (get-in module [:network :services])
                                     (map (fn [[service-name]]
                                            {:value (name service-name)
                                             :label (name service-name)}))))
            keyword)

        service (get-in module [:network :services service-name])

        endpoint-name
        (-> (prompt/list-select "Select Default Endpoint"
                                (->> (:endpoints service)
                                     (map (fn [[endpoint-name]]
                                            {:value (name endpoint-name)
                                             :label (name endpoint-name)}))))
            keyword)

        updated-state
        (assoc-in state [:network :services service-name :default-endpoint]
                  endpoint-name)]

    (api.state/save-state group-name updated-state)

    (let [module (metamerge/meta-merge module updated-state)]
      (api.proxy/write-proxy-config! {:group-name group-name
                                      :module module}))))

(defn list-services [props]
  (let [group-name (prompt.config/get-group-name props)

        {:keys [modules]} (api.resolver/pull! group-name {})
        module (api.module/get-resolved-module group-name modules)

        services (->> (get-in module [:network :services])
                      (map (fn [[service-name service]]
                             (merge service {:name (name service-name)}))))]

    (pprint/print-table [:name :default-endpoint] services)))

(defn list-endpoints [props]
  (let [group-name (prompt.config/get-group-name props)

        {:keys [modules]} (api.resolver/pull! group-name {})
        module (api.module/get-resolved-module group-name modules)

        selected-service-name (keyword (:service props))

        endpoints (->> (get-in module [:network :services])
                       (filter (fn [[service-name]]
                                 (if selected-service-name
                                   (= service-name selected-service-name)
                                   true)))

                       (map (fn [[service-name service]]
                              (->> (:endpoints service)
                                   (map (fn [[endpoint-name endpoint]]
                                          {:service service-name
                                           :endpoint endpoint-name
                                           :url (:url endpoint)
                                           :is-default (= (:default-endpoint service) endpoint-name)})))))
                       flatten)]

    (pprint/print-table [:service :endpoint :url :is-default] endpoints)))

(defn list-routes [props]
  (let [group-name (prompt.config/get-group-name props)

        {:keys [modules]} (api.resolver/pull! group-name {})
        module (api.module/get-resolved-module group-name modules)

        state (api.state/get-state group-name)

        routes (->> (get-in module [:network :routes])
                    (map (fn [[route-name route]]
                           (merge route
                                  {:name (name route-name)
                                   :enabled (get-in state [:network :routes route-name :enabled] true)}))))]

    (pprint/print-table [:name :host :prefix :service :endpoint :enabled] routes)))

(def cmd
  {:command "network"
   :description "Manage networking components"

   :subcommands [{:command "services"
                  :description "Manage network services"

                  :subcommands [{:command "list"
                                 :description "List all services"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}]

                                 :runs list-services}

                                {:command "set-endpoint"
                                 :description "Set the default endpoint for a service"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}]

                                 :runs set-default-service-endpoint!}]}

                 {:command "endpoints"
                  :description "Manage network endpoints"

                  :subcommands [{:command "list"
                                 :description "List all endpoints"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}

                                        {:option "service"
                                         :type :string}]

                                 :runs list-endpoints}]}

                 {:command "routes"
                  :description "Manage network routes"

                  :subcommands [{:command "list"
                                 :description "List all routes"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}]

                                 :runs list-routes}

                                {:command "configure"
                                 :description "Select which routes are enabled or disabled"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}]

                                 :runs (fn [_])}

                                {:command "set-service"
                                 :description "Set the service for a route"

                                 :opts [{:option "group"
                                         :short 0
                                         :type :string}]

                                 :runs (fn [_])} {:command "set-endpoint"
                                                  :description "Set the endpoint for a route"

                                                  :opts [{:option "group"
                                                          :short 0
                                                          :type :string}]

                                                  :runs (fn [_])}]}]})
