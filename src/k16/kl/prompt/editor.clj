(ns k16.kl.prompt.editor
  (:require
   [babashka.process :as proc]
   [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defn gen-hash [n]
  (->> (repeatedly n #(rand-int 256))
       (map #(format "%02x" %))
       (apply str)))

(defn open-editor [{:keys [contents filetype]}]
  (let [tmp-file (io/file (System/getProperty "java.io.tmpdir") (str "tmp-" (gen-hash 5) (if filetype filetype "")))]

    (when contents
      (spit tmp-file contents))

    (proc/shell ["nvim" (.toString tmp-file)])

    (slurp tmp-file)))
