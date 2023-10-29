(ns k16.kl.api.fs
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn from-config-dir ^java.io.File [& segments]
  (let [file (io/file (System/getProperty "user.home") ".config/kl/" (str/join "/" (flatten segments)))]
    (io/make-parents file)
    file))

(defn get-config-file ^java.io.File []
  (from-config-dir "config.edn"))

(defn get-root-module-file ^java.io.File [group-name]
  (from-config-dir group-name "module.edn"))

(defn get-lock-file ^java.io.File [group-name]
  (from-config-dir group-name "module.lock.edn"))

(defn from-work-dir ^java.io.File [group-name & segments]
  (from-config-dir group-name ".kl" (flatten segments)))

(defn from-module-dir ^java.io.File [group-name module-name & segments]
  (from-work-dir group-name ".modules" (name module-name) (flatten segments)))

(defn from-module-build-dir ^java.io.File [group-name module-name & segments]
  (from-module-dir group-name module-name "build" segments))

(defn read-edn [^java.io.File file]
  (try
    (edn/read-string (slurp file))
    (catch Exception _ {})))

(defn write-edn [^java.io.File file data]
  (let [contents (with-out-str (pprint/pprint data))]
    (spit file contents)))

(defn list-configuration-groups []
  (let [dir (from-config-dir)]
    (->> (.listFiles dir)
         (filter (fn [^java.io.File file]
                   (.isDirectory file)))
         (map (fn [^java.io.File file]
                (.getName file)))
         (filter (fn [name]
                   (not (str/starts-with? name ".")))))))
