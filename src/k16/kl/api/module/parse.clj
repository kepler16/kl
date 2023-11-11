(ns k16.kl.api.module.parse
  (:require
   [clojure.walk :as walk]
   [k16.kl.api.module.schema :as module.schema]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]))

(defn parse-module-data [module-data]
  (try
    (m/coerce module.schema/?PartialModule
              (walk/keywordize-keys module-data)
              mt/json-transformer)
    (catch Exception e
      (let [{:keys [type data]} (ex-data e)]
        (when-not (= type :malli.core/invalid-input)
          (throw e))

        (throw (ex-info "Module invalid" {:reason (me/humanize (:explain data))}))))))
