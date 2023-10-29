(ns k16.kl.api.github
  (:require
   [babashka.process :as proc]
   [clojure.string :as str]
   [org.httpkit.client :as http]))

(set! *warn-on-reflection* true)

(def ^:private cached-auth-token
  (delay
    (let [res (proc/sh ["gh" "auth" "token"])]
      (-> res :out str/trim))))

(defn- get-auth-token []
  @cached-auth-token)

(defn request [{:keys [path headers]}]
  (let [res
        @(http/request
          {:method :get
           :url (str "https://api.github.com" path)
           :headers (merge {"X-GitHub-Api-Version" "2022-11-28"
                            "Accept" "application/vnd.github+json"
                            "Authorization" (str "Bearer " (get-auth-token))}
                           headers)})]
    (when (:error res)
      (throw (:error res)))

    res))
