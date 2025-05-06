(ns k16.kl.commands.endpoints
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.prompt.config :as prompt.config]))

(defn- list-endpoints [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        selected-service-name (keyword (:service props))

        endpoints (->> (get-in module [:network :services])
                       (filterv (fn [[service-name]]
                                  (if selected-service-name
                                    (= service-name selected-service-name)
                                    true)))

                       (mapcat (fn [[service-name service]]
                                 (mapv (fn [[endpoint-name endpoint]]
                                         {:service service-name
                                          :endpoint endpoint-name
                                          :url (:url endpoint)
                                          :is-default (= (:default-endpoint service) endpoint-name)})
                                       (:endpoints service)))))]

    (pprint/print-table [:service :endpoint :url :is-default] endpoints)))

(def cmd
  {:command "endpoints"
   :description "Manage network endpoints"

   :subcommands [{:command "list"
                  :description "List all endpoints"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}

                         {:option "service"
                          :short 0
                          :type :string}]

                  :runs list-endpoints}]})
