(ns k16.kl.prompt
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
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
             (str/join "\n"))

        title (str "'" title "'")
        height (+ (count options) 1)

        opts {:in input
              :out :string
              :err :inherit
              :continue true}]

    (if multi-select?
      (let [options (str/join " " (map :label options))
            preselected (str/join "," selected)

            cmd (str "gum choose --no-limit --header " title " --height " height " --selected " preselected " " options)
            {:keys [out exit]} (proc/shell opts cmd)]

        (if (= 0 exit)
          (->> (str/split out #"\n")
               (map (fn [result]
                      (get indexed result))))
          nil))

      (let [cmd (str "gum filter --header " title " --height " height)
            {:keys [exit out]} (proc/shell opts cmd)]
        (if (= 0 exit)
          (get indexed (str/trim out))
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

        title (str "'" title "'")
        height (str (+ (count options) 3))

        cmd (str "fzf --layout reverse --header " title " --height " height)
        opts {:in input
              :out :string
              :err :inherit
              :continue true}

        {:keys [out]} (proc/shell opts cmd)]

    (get indexed (str/trim-newline out))))

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
