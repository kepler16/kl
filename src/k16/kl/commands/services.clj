(ns k16.kl.commands.services
  (:require
   [cli-matic.utils :as cli.utils]
   [clojure.pprint :as pprint]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.log :as log]
   [k16.kl.prompt :as prompt]
   [k16.kl.prompt.config :as prompt.config]
   [meta-merge.core :as metamerge]))

(defn- set-default-service-endpoint! [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        state (api.state/get-state module-name)

        service-name
        (or (-> props :service keyword)
            (-> (prompt/select "Select Service"
                               (mapv (fn [[service-name]]
                                       {:value (name service-name)
                                        :label (name service-name)})
                                     (get-in module [:network :services])))
                keyword))

        _ (when-not service-name (cli.utils/exit! "No service selected" 1))
        _ (log/info (str "Using @|bold " (name service-name) "|@"))

        service (get-in module [:network :services service-name])

        endpoint-name
        (or (-> props :endpoint keyword)
            (-> (prompt/select "Select Default Endpoint"
                               (mapv (fn [[endpoint-name]]
                                       {:value (name endpoint-name)
                                        :label (name endpoint-name)})
                                     (:endpoints service)))
                keyword))

        _ (when-not endpoint-name (cli.utils/exit! "No endpoint selected" 1))
        _ (log/info (str "Using @|bold " (name endpoint-name) "|@"))

        updated-state
        (assoc-in state [:network :services service-name :default-endpoint]
                  endpoint-name)]

    (api.state/save-state module-name updated-state)

    (let [module (metamerge/meta-merge module updated-state)]
      (api.proxy/write-proxy-config! {:module-name module-name
                                      :module module}))

    (log/info "@|green Service default-endpoint has been updated |@")))

(defn- list-service-list [services]
  (doseq [service services]
    (println (:name service))))

(defn- list-service-table [services]
  (pprint/print-table [:name :default-endpoint] services))

(defn list-services [props]
  (let [module-name (prompt.config/get-module-name props)

        {:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)

        services (mapv (fn [[service-name service]]
                         (merge service {:name (name service-name)}))
                       (get-in module [:network :services]))]

    (case (:output props)
      "names" (list-service-list services)
      "table" (list-service-table services))))

(def cmd
  {:command "services"
   :description "Manage network services"

   :subcommands [{:command "list"
                  :description "List all services"

                  :opts [{:option "output"
                          :short "o"
                          :default "table"
                          :type :string},
                         {:option "module"
                          :short "m"
                          :type :string}]

                  :runs list-services}

                 {:command "set-endpoint"
                  :description "Set the default endpoint for a service"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}
                         {:option "service"
                          :short 0
                          :type :string}
                         {:option "endpoint"
                          :short 1
                          :type :string}]

                  :runs set-default-service-endpoint!}]})
