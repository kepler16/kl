(ns k16.kl.prompt.proc
  (:require
   [clojure.string :as str]
   [promesa.core :as p])
  (:import
   [java.io BufferedWriter InputStream OutputStreamWriter]
   [java.lang ProcessBuilder ProcessBuilder$Redirect]))

(set! *warn-on-reflection* true)

(defn- write-initial-input [output-stream ^String data]
  (with-open [writer (BufferedWriter. (OutputStreamWriter. output-stream))]
    (.write writer data)
    (.flush writer)))

(defn- read-process-output [^InputStream input-stream]
  (p/vthread
   (let [buffer (byte-array 1024)]
     (loop [result (transient [])]
       (let [n-read (.read input-stream buffer)]

         (if (> n-read -1)
           (let [chunk (subvec (vec buffer) 0 n-read)]
             (recur (conj! result (String. (byte-array chunk)))))

           (str/join (persistent! result))))))))

(defn shell [{:keys [stdin cmd]}]
  (let [process-builder (ProcessBuilder. ^java.util.List cmd)]

    (.redirectInput process-builder ProcessBuilder$Redirect/PIPE)
    (.redirectError process-builder ProcessBuilder$Redirect/INHERIT)

    (let [process (.start process-builder)
          stdout-promise (read-process-output (.getInputStream process))]
      
      (write-initial-input (.getOutputStream process) stdin)

      {:code (.waitFor process)
       :stdout @stdout-promise})))
