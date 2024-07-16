(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"
                            :aliases [:native]}))
(def class-dir "target/classes")
(def jar-file "target/kl.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn build [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src" "resources"]
                  :class-dir class-dir
                  :ns-compile ['k16.kl.cli]
                  :java-opts ["-Dclojure.compiler.direct-linking=true"
                              "-Dclojure.spec.skip-macros=true"]})

  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'k16.kl.cli}))
