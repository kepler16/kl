(ns k16.kl.commands.container
  (:require
   [k16.kl.api.executor :as api.executor]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [k16.kl.prompt.config :as prompt.config]
   [clojure.pprint :as pprint]
   [meta-merge.core :as metamerge]
   [pretty.cli.prompt :as prompt]))

(set! *warn-on-reflection* true)

(def ^:private list-cmd
  {:command "list"
   :description "Select containers to run"

   :opts [{:option "group"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [group-name (prompt.config/get-group-name props)

                 {:keys [modules]} (api.resolver/pull! group-name {})
                 module (api.module/get-resolved-module group-name modules)

                 state (api.state/get-state group-name)

                 containers (->> (:containers module)
                                 (map (fn [[container-name container]]
                                        (merge container
                                               {:name (name container-name)
                                                :enabled (get-in state [:containers container-name :enabled] true)}))))]

             (pprint/print-table [:name :enabled] containers)))})

(def ^:private run-cmd
  {:command "run"
   :description "Select containers to run"

   :opts [{:option "group"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [group-name (prompt.config/get-group-name props)

                 {:keys [modules]} (api.resolver/pull! group-name {})
                 module (api.module/get-resolved-module group-name modules)

                 state (api.state/get-state group-name)

                 options (->> (:containers module)
                              (map (fn [[container-name]]
                                     {:value (name container-name)
                                      :label (name container-name)
                                      :checked (get-in state [:containers container-name :enabled] true)})))
                 selected-containers (->> options
                                          (prompt/list-checkbox "Select Services")
                                          set)

                 updated-state
                 (assoc state :containers
                        (->> (:containers module)
                             (map (fn [[container-name]]
                                    (let [enabled (boolean (some #{(name container-name)} selected-containers))]
                                      [container-name {:enabled enabled}])))
                             (into {})))]

             (api.state/save-state group-name updated-state)

             (let [module (metamerge/meta-merge module updated-state)]
               (api.proxy/write-proxy-config! {:group-name group-name
                                               :module module})
               (api.executor/start-configuration! {:group-name group-name
                                                   :module module}))))})

(def ^:private stop-cmd
  {:command "down"
   :description "Stop all running containers "

   :opts [{:option "group"
           :short 0
           :type :string}]

   :runs (fn [props]
           (let [group-name (prompt.config/get-group-name props)
                 services (api.fs/read-edn (api.fs/get-root-module-file group-name))]
             (api.executor/stop-configuration! {:group-name group-name
                                                :services services})))})

(def cmd
  {:command "containers"
   :description "Manage module containers"

   :subcommands [list-cmd run-cmd stop-cmd]})
