(ns k16.kl.prompt
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [k16.kl.prompt.proc :as prompt.proc]
   [pretty.cli.prompt :as prompt]))

(set! *warn-on-reflection* true)

(def ^:private -gum-installed?
  (delay
    (let [res (proc/sh ["which" "gum"])]
      (= 0 (:exit res)))))

(def ^:private -fzf-installed?
  (delay
    (let [res (proc/sh ["which" "fzf"])]
      (= 0 (:exit res)))))

(defn- select-with-gum [title options {:keys [multi-select?]}]
  (let [options
        (->> options
             (map (fn [option]
                    (if (string? option)
                      {:label option}
                      option))))

        selected
        (->> options
             (filter :checked)
             (map :label))

        indexed
        (->> options
             (reduce (fn [acc option]
                       (assoc acc (:label option) (:value option)))
                     {}))

        input
        (->> options
             (map :label)
             (str/join "\n"))]

    (if multi-select?
      (let [command (concat ["gum" "choose" "--header" title]
                            (map :label options)
                            ["--no-limit" "--selected" (str/join "," selected)])

            {:keys [stdout code]}
            (prompt.proc/shell {:stdin input :cmd command})

            results (str/split stdout #"\n")]

        (if (= 0 code)
          (map (fn [result]
                 (get indexed result))
               results)
          nil))

      (let [command ["gum" "filter"]
            {:keys [code stdout]} (proc/shell {:stdin input :cmd command})]
        (if (= 0 code)
          (get indexed (str/trim stdout))
          nil)))))

(defn- select-with-fzf [title options]
  (let [options
        (->> options
             (map (fn [option]
                    (if (string? option)
                      {:label option :value option}
                      option))))

        indexed
        (->> options
             (reduce (fn [acc option]
                       (assoc acc (:label option) (:value option)))
                     {}))

        input
        (->> options
             (map :label)
             (str/join "\n"))

        {:keys [stdout]}
        (prompt.proc/shell {:cmd ["fzf" "--layout" "reverse" "--header" title "--height" (str (+ 3 (count options)))]
                            :stdin input})]

    (get indexed (str/trim-newline stdout))))

(defn select [title options]
  (cond
    @-fzf-installed?
    (select-with-fzf title options)

    @-gum-installed?
    (select-with-gum title options {})

    :else
    (prompt/list-select title options)))

(defn select-multi [title options]
  (cond
    @-gum-installed?
    (select-with-gum title options {:multi-select? true})

    :else
    (prompt/list-checkbox title options)))
