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
        (mapv (fn [option]
                (if (string? option)
                  {:label option}
                  option))
              options)

        selected
        (->> options
             (filterv :checked)
             (mapv :label))

        indexed
        (->> options
             (reduce (fn [acc option]
                       (assoc acc (:label option) (:value option)))
                     {}))

        input
        (->> options
             (mapv :label)
             (str/join "\n"))

        common-style-options ["--header.foreground='#d3869b'"]

        header (str " --header '" title "' ")
        height (str " --height " (+ (count options) 2) " ")
        preselected (if (seq selected)
                      (str " --selected " (str/join "," selected) " ")
                      "")

        opts {:in input
              :out :string
              :err :inherit
              :continue true}]

    (if multi-select?
      (let [options (str/join " " (mapv :label options))

            style-options (into common-style-options
                                ["--cursor.foreground='#b8bb26'"
                                 "--selected.foreground='#b8bb26'"])
            style (str " " (str/join " " style-options) " ")

            cmd (str "gum choose --no-limit" style header height preselected options)
            {:keys [out exit]} (proc/shell opts cmd)]

        (if (= 0 exit)
          (mapv (fn [result]
                  (get indexed result))
                (str/split out #"\n"))
          nil))

      (let [style-options (into common-style-options
                                ["--match.foreground='#fabd2f'"
                                 "--prompt.foreground='#b8bb26'"
                                 "--indicator.foreground='#fabd2f'"
                                 "--indicator='>'"])
            style (str " " (str/join " " style-options) " ")
            cmd (str "gum filter" style header height)
            {:keys [exit out]} (proc/shell opts cmd)]
        (if (= 0 exit)
          (get indexed (str/trim out))
          nil)))))

(defn- select-with-fzf [title options]
  (let [options
        (mapv (fn [option]
                (if (string? option)
                  {:label option :value option}
                  option))
              options)

        indexed
        (->> options
             (reduce (fn [acc option]
                       (assoc acc (:label option) (:value option)))
                     {}))

        input
        (->> options
             (mapv :label)
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
