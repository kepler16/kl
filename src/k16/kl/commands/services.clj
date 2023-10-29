(ns k16.kl.commands.services
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt.config :as prompt.config]
   [meta-merge.core :as metamerge]
   [pretty.cli.prompt :as prompt]))

(defn- set-default-service-endpoint! [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        state (api.state/get-state module-name)

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

    (api.state/save-state module-name updated-state)

    (let [module (metamerge/meta-merge module updated-state)]
      (api.proxy/write-proxy-config! {:module-name module-name
                                      :module module}))))

(defn- list-services [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        services (->> (get-in module [:network :services])
                      (map (fn [[service-name service]]
                             (merge service {:name (name service-name)}))))]

    (pprint/print-table [:name :default-endpoint] services)))

(def cmd
  {:command "services"
   :description "Manage network services"

   :subcommands [{:command "list"
                  :description "List all services"

                  :opts [{:option "module"
                          :short 0
                          :type :string}]

                  :runs list-services}

                 {:command "set-endpoint"
                  :description "Set the default endpoint for a service"

                  :opts [{:option "module"
                          :short 0
                          :type :string}]

                  :runs set-default-service-endpoint!}]})
