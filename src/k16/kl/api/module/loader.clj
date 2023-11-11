(ns k16.kl.api.module.loader
  (:require
   [clj-yaml.core :as yaml]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [jsonista.core :as json]
   [k16.kl.api.module.schema :as module.schema]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]))

(defn parse-module-contents [file-name module-contents]
  (let [ext (-> file-name (str/split #"\.") last keyword)

        module-data
        (cond
          (= ext :edn)
          (edn/read-string module-contents)

          (= ext :json)
          (json/read-value module-contents)

          (or (= ext :yml)
              (= ext :yaml))
          (yaml/parse-string module-contents)

          :else (throw (ex-info "Failed to parse module. Unsupported file format" file-name)))]

    (try
      (m/coerce module.schema/?PartialModule
                (walk/keywordize-keys module-data)
                mt/json-transformer)
      (catch Exception e
        (let [{:keys [type data]} (ex-data e)]
          (when-not (= type :malli.core/invalid-input)
            (throw e))

          (throw (ex-info "Module invalid" {:reason (me/humanize (:explain data))})))))))

(defn read-module-file [^java.io.File file]
  (let [name (.getName file)
        contents (slurp file)]
    (parse-module-contents name contents)))
