(ns k16.kl.commands.services
  (:require
   [cli-matic.utils :as cli.utils]
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt :as prompt]
   [k16.kl.prompt.config :as prompt.config]
   [meta-merge.core :as metamerge]))

(defn- set-default-service-endpoint! [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        state (api.state/get-state module-name)

        service-name
        (-> (prompt/select "Select Service"
                           (->> (get-in module [:network :services])
                                (map (fn [[service-name]]
                                       {:value (name service-name)
                                        :label (name service-name)}))))
            keyword)

        _ (when-not service-name (cli.utils/exit! "No service selected" 1))

        service (get-in module [:network :services service-name])

        endpoint-name
        (-> (prompt/select "Select Default Endpoint"
                           (->> (:endpoints service)
                                (map (fn [[endpoint-name]]
                                       {:value (name endpoint-name)
                                        :label (name endpoint-name)}))))
            keyword)

        _ (when-not endpoint-name (cli.utils/exit! "No endpoint selected" 1))

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
