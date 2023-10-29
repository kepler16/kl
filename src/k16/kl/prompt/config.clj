(ns k16.kl.prompt.config
  (:require
   [k16.kl.api.fs :as api.fs]
   [pretty.cli.prompt :as prompt]))

(set! *warn-on-reflection* true)

(defn get-group-name [{:keys [group skip-default?]}]
  (let [{:keys [default-module]} (api.fs/read-edn (api.fs/get-config-file))]
    (cond
      group group

      (and default-module (not skip-default?)) default-module

      :else (let [groups (api.fs/list-configuration-groups)]
              (prompt/list-select "Select Configuration Group" groups)))))
