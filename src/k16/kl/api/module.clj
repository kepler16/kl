(ns k16.kl.api.module
  (:require
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.state :as api.state]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

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
                         (get-in merged-root [:network :routes]))]

    (metamerge/meta-merge merged-root {:containers state-containers
                                       :network {:services state-services
                                                 :routes state-routes}})))

(defn get-resolved-module [module-name modules]
  (let [root-module (api.fs/read-edn (api.fs/get-root-module-file module-name))]
    (dissoc (merge-modules module-name root-module modules) :modules)))
