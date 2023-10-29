(ns k16.kl.api.module
  (:require
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.state :as api.state]
   [meta-merge.core :as metamerge]))

(set! *warn-on-reflection* true)

(defn- merge-modules [group-name root-module sub-modules]
  (let [state (api.state/get-state group-name)
        merged
        (->> sub-modules
             (reduce (fn [acc [module-name]]
                       (let [module-file (api.fs/from-submodule-dir group-name module-name "module.edn")
                             module (api.fs/read-edn module-file)]
                         (metamerge/meta-merge acc module)))
                     {}))]
    (metamerge/meta-merge
     merged
     root-module
     (select-keys state [:network :containers]))))

(defn get-resolved-module [group-name modules]
  (let [root-module (api.fs/read-edn (api.fs/get-root-module-file group-name))]
    (dissoc (merge-modules group-name root-module modules) :modules)))
