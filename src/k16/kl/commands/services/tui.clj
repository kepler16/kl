(ns k16.kl.commands.services.tui
  (:require
   [charm.core :as charm]
   [clojure.string :as str]
   [k16.kl.api.fs :as api.fs]
   [k16.kl.api.module :as api.module]
   [k16.kl.api.proxy :as api.proxy]
   [k16.kl.api.resolver :as api.resolver]
   [k16.kl.api.state :as api.state]
   [meta-merge.core :as metamerge]))

(def ^:private tab-active-style
  (charm/style :fg (charm/ansi256 208) :bold true))

(def ^:private tab-inactive-style
  (charm/style :fg 245))

(def ^:private bracket-style
  (charm/style :fg 240))

(def ^:private service-name-style
  (charm/style :fg charm/white :bold true))

(def ^:private endpoint-active-style
  (charm/style :fg charm/green :bold true))

(def ^:private endpoint-inactive-style
  (charm/style :fg 245))

(def ^:private cursor-prefix-style
  (charm/style :fg charm/yellow :bold true))

(def ^:private dim-style
  (charm/style :fg 240))

(def ^:private help-key-style
  (charm/style :fg charm/cyan :bold true))

(def ^:private separator-style
  (charm/style :fg 240))

(def ^:private detail-label-style
  (charm/style :fg 245))

(def ^:private detail-url-style
  (charm/style :fg charm/white))

(def ^:private detail-active-label-style
  (charm/style :fg charm/green :bold true))

(defn- load-module-data [module-name]
  (let [{:keys [modules]} (api.resolver/pull! module-name {})
        module (api.module/get-resolved-module module-name modules)
        state (api.state/get-state module-name)
        services (->> (get-in module [:network :services])
                      (mapv (fn [[svc-name svc]]
                              {:name svc-name
                               :endpoints (vec (keys (:endpoints svc)))
                               :endpoint-details (:endpoints svc)
                               :default-endpoint (:default-endpoint svc)}))
                      (sort-by :name)
                      vec)]
    {:module module
     :state state
     :services services}))

(defn- build-initial-state [modules default-module]
  (let [module-names (vec (sort modules))
        active-idx (or (some (fn [[i m]] (when (= m default-module) i))
                             (map-indexed vector module-names))
                       0)
        active-module (nth module-names active-idx)
        {:keys [services]} (load-module-data active-module)]
    {:module-names module-names
     :active-module-idx active-idx
     :services services
     :cursor 0
     :detail? false
     :module-services {active-module services}}))

(defn- current-module-name [state]
  (nth (:module-names state) (:active-module-idx state)))

(defn- current-service [state]
  (when (seq (:services state))
    (nth (:services state) (:cursor state))))

(defn- endpoint-index [service endpoint-name]
  (some (fn [[i ep]] (when (= ep endpoint-name) i))
        (map-indexed vector (:endpoints service))))

(defn- switch-module [state direction]
  (let [old-module (current-module-name state)
        state (assoc-in state [:module-services old-module] (:services state))
        n (count (:module-names state))
        new-idx (mod (+ (:active-module-idx state) direction) n)
        new-module (nth (:module-names state) new-idx)
        services (or (get-in state [:module-services new-module])
                     (:services (load-module-data new-module)))]
    (assoc state
           :active-module-idx new-idx
           :services services
           :cursor 0
           :detail? false
           :module-services (assoc (:module-services state) new-module services))))

(defn- move-cursor [state direction]
  (let [n (count (:services state))]
    (if (zero? n)
      state
      (assoc state
             :cursor (mod (+ (:cursor state) direction) n)
             :detail? false))))

(defn- cycle-endpoint [state direction]
  (let [service (current-service state)]
    (when service
      (let [endpoints (:endpoints service)
            n (count endpoints)
            current-idx (or (endpoint-index service (:default-endpoint service)) 0)
            new-idx (mod (+ current-idx direction) n)
            new-endpoint (nth endpoints new-idx)
            cursor (:cursor state)]
        (assoc-in state [:services cursor :default-endpoint] new-endpoint)))))

(defn- persist-all-modules! [state]
  (let [all-services (assoc (:module-services state)
                            (current-module-name state)
                            (:services state))]
    (doseq [[module-name services] all-services]
      (let [{:keys [module]} (load-module-data module-name)
            current-state (api.state/get-state module-name)
            updated-state
            (reduce (fn [s svc]
                      (assoc-in s [:network :services (:name svc) :default-endpoint]
                                (:default-endpoint svc)))
                    current-state
                    services)]
        (api.state/save-state module-name updated-state)
        (let [merged-module (metamerge/meta-merge module updated-state)]
          (api.proxy/write-proxy-config! {:module-name module-name
                                          :module merged-module}))))))

(defn- render-tabs [state]
  (let [tabs (->> (:module-names state)
                  (map-indexed
                   (fn [i module-name]
                     (if (= i (:active-module-idx state))
                       (str (charm/render bracket-style "[")
                            (charm/render tab-active-style module-name)
                            (charm/render bracket-style "]"))
                       (str (charm/render bracket-style "(")
                            (charm/render tab-inactive-style module-name)
                            (charm/render bracket-style ")"))))))]
    (str/join " " tabs)))

(defn- render-endpoint-bar [service]
  (let [endpoints (:endpoints service)
        default-ep (:default-endpoint service)]
    (->> endpoints
         (mapv (fn [ep]
                 (let [ep-str (name ep)]
                   (if (= ep default-ep)
                     (charm/render endpoint-active-style ep-str)
                     (charm/render endpoint-inactive-style ep-str)))))
         (str/join (charm/render separator-style " | ")))))

(defn- render-service-row [service selected?]
  (let [prefix (if selected?
                 (str (charm/render cursor-prefix-style "\u276f") " ")
                 "  ")
        svc-name (charm/render service-name-style
                               (format "%-20s" (name (:name service))))
        ep-bar (render-endpoint-bar service)]
    (str prefix svc-name "  " ep-bar)))

(defn- render-detail [service]
  (let [default-ep (:default-endpoint service)
        details (:endpoint-details service)]
    (->> (:endpoints service)
         (mapv (fn [ep-name]
                 (let [url (get-in details [ep-name :url])
                       active? (= ep-name default-ep)
                       label-style (if active? detail-active-label-style detail-label-style)
                       marker (if active? "*" " ")]
                   (str "      " marker " "
                        (charm/render label-style (format "%-14s" (name ep-name)))
                        " " (charm/render detail-url-style url)))))
         (str/join "\n"))))

(defn- render-services [state]
  (if (empty? (:services state))
    (charm/render dim-style "  No services configured")
    (->> (:services state)
         (map-indexed
          (fn [i service]
            (let [selected? (= i (:cursor state))
                  row (render-service-row service selected?)]
              (if (and selected? (:detail? state))
                (str row "\n" (render-detail service))
                row))))
         (str/join "\n"))))

(defn- render-help [detail?]
  (let [bindings (if detail?
                   [["h/l" "cycle endpoint"] ["enter" "close"] ["j/k" "navigate"] ["q" "save & quit"]]
                   [["h/l" "cycle endpoint"] ["enter" "details"] ["j/k" "navigate"] ["tab" "module"] ["q" "save & quit"]])]
    (->> bindings
         (mapv (fn [[k desc]]
                 (str (charm/render help-key-style k) " " desc)))
         (str/join (charm/render separator-style " \u2022 ")))))

(defn- view [state]
  (str (render-tabs state) "\n"
       "\n"
       (render-services state) "\n"
       "\n"
       (render-help (:detail? state))))

(defn- update-fn [state msg]
  (cond
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c"))
    (do (persist-all-modules! state)
        [state charm/quit-cmd])

    (charm/key-match? msg "tab")
    [(switch-module state 1) nil]

    (charm/key-match? msg "shift+tab")
    [(switch-module state -1) nil]

    (or (charm/key-match? msg "l")
        (charm/key-match? msg :right))
    [(or (cycle-endpoint state 1) state) nil]

    (or (charm/key-match? msg "h")
        (charm/key-match? msg :left))
    [(or (cycle-endpoint state -1) state) nil]

    (or (charm/key-match? msg "j")
        (charm/key-match? msg :down))
    [(move-cursor state 1) nil]

    (or (charm/key-match? msg "k")
        (charm/key-match? msg :up))
    [(move-cursor state -1) nil]

    (charm/key-match? msg "enter")
    [(update state :detail? not) nil]

    :else
    [state nil]))

(defn run-tui! [{:keys [module]}]
  (let [{:keys [default-module]} (api.fs/read-edn (api.fs/get-config-file))
        modules (api.fs/list-modules)
        default (or module default-module (first (sort modules)))
        initial-state (build-initial-state modules default)]
    (charm/run {:init (fn [] [initial-state nil])
                :update update-fn
                :view view
                :alt-screen true})))
