(ns kl.cli
  (:gen-class)
  (:require [babashka.cli :as cli]
            [kl.commands.resolver :as resolver]
            [kl.commands.network :as network]))

(def commands (concat resolver/commands
                      network/commands))

(defn run [args]
  (cli/dispatch commands args))

(defn -main [& args]
  (run args)
  nil)

(comment
  (run ["setup-resolver"])
  (run ["up"])
  (run ["down"]))
