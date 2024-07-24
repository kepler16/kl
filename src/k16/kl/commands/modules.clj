(ns k16.kl.commands.modules
  (:require
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.log :as log]
   [k16.kl.prompt.config :as prompt.config]))

(set! *warn-on-reflection* true)

(defn- pull! [{:keys [update] :as props}]
  (let [module-name (prompt.config/get-module-name props)
        {:keys [lockfile-updated?]} (api.resolver/pull! module-name {:update-lockfile? update})]
    (if lockfile-updated?
      (log/info "@|bold Services updated|@")
      (log/info "Services are all up to date"))))

(defn- set-default-module! [props]
  (let [module-name (prompt.config/get-module-name (assoc props :skip-default? true))]
    (api.fs/write-edn (api.fs/get-config-file) {:default-module module-name})))

(def cmd
  {:command "module"
   :description "Manage module configurations"

   :subcommands [{:command "set-default"
                  :description "Set the default module"
                  :opts [{:option "module"
                          :short 0
                          :type :string}]
                  :runs set-default-module!}

                 {:command "pull"
                  :description "Pull down changes to a module"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}

                         {:option "update"
                          :default false
                          :short 0
                          :type :with-flag}]

                  :runs pull!}

                 {:command "update"
                  :description "Resolve the latest sha's of a module. This is the same as `pull --update`"

                  :opts [{:option "module"
                          :short "m"
                          :type :string}

                         {:option "force"
                          :default false
                          :short 0
                          :type :with-flag}]

                  :runs (fn [props]
                          (pull! (assoc props :update true)))}]})
