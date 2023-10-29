(ns k16.kl.log)

(def ^:private lock (Object.))

(defn info [& args]
  (locking lock
    (apply println args)))
