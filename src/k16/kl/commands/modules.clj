(ns k16.kl.commands.modules
  (:require
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.prompt.config :as prompt.config]))

(set! *warn-on-reflection* true)

(defn- pull! [{:keys [update force] :as props}]
  (let [group-name (prompt.config/get-group-name props)
        updated? (api.resolver/pull! group-name {:update-lockfile? update
                                                 :force? force})]
    (if updated?
      (println "Services updated")
      (println "Services are all up to date"))))

(defn- set-default-module! [props]
  (let [group-name (prompt.config/get-group-name (assoc props :skip-default? true))]
    (api.fs/write-edn (api.fs/get-config-file) {:default-module group-name})))

(def cmd
  {:command "module"
   :description "Manage module configurations"

   :subcommands [{:command "set-default"
                  :description "Set the default module"
                  :opts [{:option "group"
                          :short 0
                          :type :string}]
                  :runs set-default-module!}

                 {:command "pull"
                  :description "Pull down changes to a module"

                  :opts [{:option "group"
                          :short 0
                          :type :string}

                         {:option "update"
                          :default false
                          :type :with-flag}

                         {:option "force"
                          :default false
                          :type :with-flag}]

                  :runs pull!}

                 {:command "update"
                  :description "Resolve the latest sha's of a module. This is the same as `pull --update`"

                  :opts [{:option "group"
                          :short 0
                          :type :string}

                         {:option "force"
                          :default false
                          :type :with-flag}]

                  :runs (fn [props]
                          (pull! (assoc props :update true)))}]})
