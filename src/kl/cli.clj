(ns kl.cli
  (:require
   [babashka.cli :as cli]
   [kl.commands.resolver :as resolver]
   [kl.commands.network :as network]
   [kl.commands.proxy :as proxy]
   [clojure.string :as str])
  (:gen-class))

(defn print-help [_]
  (println (str/trim "
Usage: kl <command> <options>

Commands:

network:
  start   Start all docker network components
  stop    Stop any running docker network components

resolver:
  setup   Output a bash/shell command to use for setting up the host DNS resolver

proxy:
  list    List all proxy configurations including their active statuses
  create  Create a new proxy config
    Options:
    --name    The name of the proxy
    --domain  The domain to route on. For example: example.k44.test
    --url     The URL to proxy requests to. For example:
                http://host.docker.internal:8765
              You can leave URL components blank to use their default:
                --url=:8765 will default to http://host.docker.internal:8765
  delete  Delete a named proxy configuration
  disable Disable proxy configurations. This will keep it defined but it won't affect routing
  enable  Re-enable disabled proxy configurations.
")))

(def commands (concat resolver/commands
                      network/commands
                      proxy/commands
                      [{:cmds [] :fn print-help}]))

(defn run [args]
  (cli/dispatch commands args))

(defn -main [& args]
  (run args)
  nil)
