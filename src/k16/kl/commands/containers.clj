(ns k16.kl.commands.containers
  (:require
   [cli-matic.utils :as cli.utils]
   [clojure.pprint :as pprint]
   [k16.kl.api.executor :as api.executor]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt :as prompt]
   [k16.kl.prompt.config :as prompt.config]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

(def ^:private list-cmd
  {:command "list"
   :description "Select containers to run"

   :opts [{:option "module"
           :short "m"
           :type :string}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)

                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)

                 state (api.state/get-state module-name)

                 containers (mapv (fn [[container-name container]]
                                    (merge container
                                           {:name (name container-name)
                                            :enabled (get-in state [:containers container-name :enabled]
                                                             (get-in module [:containers container-name :enabled] true))}))
                                  (:containers module))]

             (pprint/print-table [:name :enabled] containers)))})

(def ^:private run-cmd
  {:command "run"
   :description "Select containers to run"

   :opts [{:option "module"
           :short "m"
           :type :string}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)

                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)

                 state (api.state/get-state module-name)

                 options (mapv (fn [[container-name]]
                                 {:value (name container-name)
                                  :label (name container-name)
                                  :checked (get-in state [:containers container-name :enabled] true)})
                               (:containers module))

                 selection-result (prompt/select-multi "Select Services" options)
                 _ (when-not selection-result
                     (cli.utils/exit! "Cancelled" 1))

                 selected-containers (into #{}
                                           (map keyword)
                                           selection-result)

                 updated-state
                 (assoc state :containers
                        (into {}
                              (map (fn [[container-name]]
                                     (let [enabled (boolean (some #{container-name} selected-containers))]
                                       [container-name {:enabled enabled}])))
                              (:containers module)))]

             (api.state/save-state module-name updated-state)

             (let [module (metamerge/meta-merge module updated-state)]
               (api.proxy/write-proxy-config! {:module-name module-name
                                               :module module})
               (api.executor/run-module! {:module-name module-name
                                          :module module
                                          :direction :up}))))})

(def ^:private stop-cmd
  {:command "stop"
   :description "Stop all running containers "

   :opts [{:option "module"
           :short "m"
           :type :string}

          {:option "delete-volumes"
           :type :with-flag
           :default false}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)
                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)]
             (api.executor/run-module! {:module-name module-name
                                        :module module
                                        :direction :down
                                        :delete-volumes (:delete-volumes props)})))})

(def cmd
  {:command "containers"
   :description "Manage module containers"

   :subcommands [list-cmd run-cmd stop-cmd]})
