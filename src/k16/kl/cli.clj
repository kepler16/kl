(ns k16.kl.cli
  (:require
   [cli-matic.core :refer [run-cmd]]
   [k16.kl.commands.containers :as cmd.containers]
   [k16.kl.commands.endpoints :as cmd.endpoints]
   [k16.kl.commands.modules :as cmd.modules]
   [k16.kl.commands.network :as cmd.network]
   [k16.kl.commands.resolver :as cmd.resolver]
   [k16.kl.commands.routes :as cmd.routes]
   [k16.kl.commands.services :as cmd.services])
  (:gen-class))

(set! *warn-on-reflection* true)

(def cli-configuration
  {:command "kl"
   :description "A CLI for running remotely defined services"
   :version "0.0.0"
   :subcommands [cmd.modules/cmd
                 cmd.network/cmd
                 cmd.resolver/cmd
                 cmd.containers/cmd
                 cmd.services/cmd
                 cmd.endpoints/cmd
                 cmd.routes/cmd]})

(defn -main [& args]
  (run-cmd args cli-configuration))
