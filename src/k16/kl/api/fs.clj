(ns k16.kl.api.fs
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn from-config-dir
  ^java.io.File [& segments]
  (let [file (io/file (System/getProperty "user.home")
                      ".config/kl/"
                      (str/join "/" (flatten segments)))]
    (io/make-parents file)
    file))

(defn from-modules-dir
  ^java.io.File [& segments]
  (from-config-dir "modules" (flatten segments)))

(defn get-config-file ^java.io.File []
  (from-config-dir "config.edn"))

(defn get-root-module-file
  ^java.io.File [group-name]
  (let [dir (from-modules-dir group-name)
        file-name
        (->> (.listFiles dir)
             (mapv (fn [^java.io.File file] (.getName file)))
             (filterv (fn [file-name]
                        (re-matches #"module\.(edn|yaml|yml|json)"
                                    file-name)))
             first)]
    (from-modules-dir group-name file-name)))

(defn get-lock-file
  ^java.io.File [group-name]
  (from-modules-dir group-name "module.lock.edn"))

(defn from-module-work-dir
  ^java.io.File [module-name & segments]
  (from-modules-dir module-name ".kl" (flatten segments)))

(defn from-submodule-dir
  ^java.io.File [module-name submodule-name & segments]
  (from-module-work-dir module-name
                        ".modules"
                        (name submodule-name)
                        (flatten segments)))

(defn read-edn [^java.io.File file]
  (try
    (edn/read-string (slurp file))
    (catch Exception _ {})))

(defn write-edn [^java.io.File file data]
  (let [contents (with-out-str (pprint/pprint data))]
    (spit file contents)))

(defn rm-dir! [^java.io.File file]
  (when (.isDirectory file)
    (run! rm-dir! (.listFiles file)))
  (io/delete-file file))

(defn list-modules []
  (let [dir (from-modules-dir)]
    (->> (.listFiles dir)
         (filterv (fn [^java.io.File file]
                    (.isDirectory file)))
         (mapv (fn [^java.io.File file]
                 (.getName file)))
         (filterv (fn [name]
                    (not (str/starts-with? name ".")))))))
