(ns k16.kl.prompt.config
  (:require
   [k16.kl.api.fs :as api.fs]
   [k16.kl.prompt :as prompt]
   [cli-matic.utils :as cli.utils]))

(set! *warn-on-reflection* true)

(defn get-module-name [{:keys [module skip-default?]}]
  (let [{:keys [default-module]} (api.fs/read-edn (api.fs/get-config-file))]
    (cond
      module module

      (and default-module (not skip-default?)) default-module

      :else (let [modules (api.fs/list-modules)
                  module-name (prompt/select "Select Module" modules)]
              (when-not module-name (cli.utils/exit! "No endpoint selected" 1))
              module-name))))
