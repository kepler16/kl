(ns k16.kl.commands.containers
  (:require
   [clojure.pprint :as pprint]
   [k16.kl.api.executor :as api.executor]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt.config :as prompt.config]
   [meta-merge.core :as metamerge]
   [pretty.cli.prompt :as prompt]))

(set! *warn-on-reflection* true)

(def ^:private list-cmd
  {:command "list"
   :description "Select containers to run"

   :opts [{:option "module"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)

                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)

                 state (api.state/get-state module-name)

                 containers (->> (:containers module)
                                 (map (fn [[container-name container]]
                                        (merge container
                                               {:name (name container-name)
                                                :enabled (get-in state [:containers container-name :enabled] true)}))))]

             (pprint/print-table [:name :enabled] containers)))})

(def ^:private run-cmd
  {:command "run"
   :description "Select containers to run"

   :opts [{:option "module"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)

                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)

                 state (api.state/get-state module-name)

                 options (->> (:containers module)
                              (map (fn [[container-name]]
                                     {:value (name container-name)
                                      :label (name container-name)
                                      :checked (get-in state [:containers container-name :enabled] true)})))
                 selected-containers (->> options
                                          (prompt/list-checkbox "Select Services")
                                          (map keyword)
                                          set)

                 updated-state
                 (assoc state :containers
                        (->> (:containers module)
                             (map (fn [[container-name]]
                                    (let [enabled (boolean (some #{container-name} selected-containers))]
                                      [container-name {:enabled enabled}])))
                             (into {})))]

             (api.state/save-state module-name updated-state)

             (let [module (metamerge/meta-merge module updated-state)]
               (api.proxy/write-proxy-config! {:module-name module-name
                                               :module module})
               (api.executor/start-configuration! {:module-name module-name
                                                   :module module}))))})

(def ^:private stop-cmd
  {:command "down"
   :description "Stop all running containers "

   :opts [{:option "module"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [module-name (prompt.config/get-module-name props)
                 {:keys [modules]} (api.resolver/pull! module-name {})
                 module (api.module/get-resolved-module module-name modules)]
             (api.executor/stop-configuration! {:module-name module-name
                                                :module module})))})

(def cmd
  {:command "containers"
   :description "Manage module containers"

   :subcommands [list-cmd run-cmd stop-cmd]})