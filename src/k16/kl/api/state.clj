(ns k16.kl.api.state
  (:require
   [k16.kl.api.fs :as api.fs]))

(defn get-state-file [group-name]
  (api.fs/from-work-dir group-name "state.edn"))

(defn get-state [group-name]
  (api.fs/read-edn (get-state-file group-name)))

(defn save-state [group-name state]
  (api.fs/write-edn (get-state-file group-name) state))
