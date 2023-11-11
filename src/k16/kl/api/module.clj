(ns k16.kl.api.module
  (:require
   [clj-yaml.core :as yaml]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [jsonista.core :as json]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.module.parse :as module.parse]
   [k16.kl.api.module.schema :as module.schema]
   [k16.kl.api.state :as api.state]
   [malli.core :as m]
   [malli.error :as me]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

(defn read-module-file [^java.io.File file]
  (let [name (.getName file)
        contents (slurp file)

        ext (-> name (str/split #"\.") last keyword)

        data (cond
               (= ext :edn)
               (edn/read-string contents)

               (= ext :json)
               (json/read-value contents)

               (or (= ext :yml)
                   (= ext :yaml))
               (yaml/parse-string contents)

               :else (throw (ex-info "Failed to read module file. Unsupported file format" {:file-name name})))]

    (module.parse/parse-module-data data)))

(defn- filter-by-known [left right]
  (->> left
       (filter (fn [[container-name]]
                 (contains? right container-name)))
       (into {})))

(defn- merge-modules [module-name root-module sub-modules]
  (let [state (api.state/get-state module-name)

        merged-submodules
        (->> sub-modules
             (reduce (fn [acc [submodule-name]]
                       (let [module-file (api.fs/from-submodule-dir module-name submodule-name "module.edn")
                             module (api.fs/read-edn module-file)]
                         (metamerge/meta-merge acc module)))
                     {}))

        merged-root
        (metamerge/meta-merge merged-submodules root-module)

        state-containers
        (filter-by-known (:containers state) (:containers merged-root))

        state-services
        (filter-by-known (get-in state [:network :services])
                         (get-in merged-root [:network :services]))

        state-routes
        (filter-by-known (get-in state [:network :routes])
                         (get-in merged-root [:network :routes]))

        final
        (metamerge/meta-merge merged-root {:containers state-containers
                                           :network {:services state-services
                                                     :routes state-routes}})]

    (when-not (m/validate module.schema/?Module final)
      (throw (ex-info "Module invalid" {:reason (->> final
                                                     (m/explain module.schema/?Module)
                                                     me/humanize)})))

    final))

(defn get-resolved-module [module-name modules]
  (let [root-module (read-module-file (api.fs/get-root-module-file module-name))]
    (dissoc (merge-modules module-name root-module modules) :modules)))
