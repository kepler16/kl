(ns k16.kl.cli
  (:require
   [babashka.process :as proc]
   [cli-matic.core :refer [run-cmd]]
   [clojure.string :as str]
   [k16.kl.commands.containers :as cmd.containers]
   [k16.kl.commands.endpoints :as cmd.endpoints]
   [k16.kl.commands.modules :as cmd.modules]
   [k16.kl.commands.network :as cmd.network]
   [k16.kl.commands.resolver :as cmd.resolver]
   [k16.kl.commands.routes :as cmd.routes]
   [k16.kl.commands.services :as cmd.services]
   [k16.kl.log :as log])
  (:gen-class))

(set! *warn-on-reflection* true)

(defmacro version []
  (let [res (proc/sh (str/split "git describe --abbrev=0 --tags" #" "))]
    (str/replace (str/trim (:out res)) #"v" "")))

(def cli-configuration
  {:command "kl"
   :description "A CLI for running remotely defined services"
   :version (version)
   :subcommands [{:command "version"
                  :description "Print the current CLI version"
                  :runs (fn [_]
                          (log/info (version)))}

                 cmd.modules/cmd
                 cmd.network/cmd
                 cmd.resolver/cmd
                 cmd.containers/cmd
                 cmd.services/cmd
                 cmd.endpoints/cmd
                 cmd.routes/cmd]})

(defn -main [& args]
  (run-cmd args cli-configuration))
