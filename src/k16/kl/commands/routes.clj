(ns k16.kl.commands.routes
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt.config :as prompt.config]))

(defn- list-routes [props]
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

                                   :runs (fn [_])}]})
