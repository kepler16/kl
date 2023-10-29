(ns k16.kl.api.state
  (:require
   [k16.kl.api.fs :as api.fs]))

(defn get-state-file [module-name]
  (api.fs/from-module-work-dir module-name "state.edn"))

(defn get-state [module-name]
  (api.fs/read-edn (get-state-file module-name)))

(defn save-state [module-name state]
  (api.fs/write-edn (get-state-file module-name) state))
