(ns k16.kl.log
  (:require
   [jansi-clj.core :as color]))

(def ^:private lock (Object.))

(defn info [msg]
  (locking lock
    (println (color/render msg))))

(defn error [msg]
  (locking lock
    (println (color/render (str "@|red " (color/render msg) "|@")))))

(defn warn [msg]
  (locking lock
    (println (color/render (str "@|yellow " (color/render msg) "|@")))))

(defn debug [msg]
  (locking lock
    (println (color/render (str "@|white " (color/render msg) "|@")))))
