(ns k16.kl.prompt.config
  (:require
   [k16.kl.api.fs :as api.fs]
   [pretty.cli.prompt :as prompt]))

(set! *warn-on-reflection* true)

(defn get-module-name [{:keys [module skip-default?]}]
  (let [{:keys [default-module]} (api.fs/read-edn (api.fs/get-config-file))]
    (cond
      module module

      (and default-module (not skip-default?)) default-module

      :else (let [modules (api.fs/list-modules)]
              (prompt/list-select "Select Module" modules)))))
