(ns k16.kl.api.module.schema
  (:require
   [malli.core :as m]
   [malli.util :as mu]))

(set! *warn-on-reflection* true)

(defn- make-recursively-optional [schema]
  (let [modifier
        (fn [schema]
          (let [type (m/type schema)
                properties (m/properties schema)]
            (cond
              (:kl/locked properties)
              schema

              (= :map type)
              (mu/optional-keys schema)

              :else
              schema)))]

    (m/walk schema (m/schema-walker modifier))))

(def ?ModuleRef
  [:map {:kl/locked true}
   [:url :string]
   [:sha {:optional true} :string]
   [:ref {:optional true} :string]
   [:subdir {:optional true} :string]])

(def ?Endpoint
  [:map
   [:url :string]])

(def ?Service
  [:map
   [:endpoints [:map-of :keyword ?Endpoint]]
   [:default-endpoint :keyword]])

(def ?Route
  [:map
   [:host :string]
   [:path-prefix {:optional true} :string]
   [:service :keyword]
   [:endpoint {:optional true} :keyword]])

(def ?Network
  [:map
   [:services {:optional true} [:map-of :keyword ?Service]]
   [:routes {:optional true} [:map-of :keyword ?Route]]])

(def ?Module
  [:map
   [:modules {:optional true} [:map-of :keyword ?ModuleRef]]

   [:include {:optional true} [:sequential :string]]

   [:network {:optional true} ?Network]

   [:volumes {:optional true} [:map-of :keyword [:map]]]
   [:containers {:optional true} [:map-of :keyword [:map]]]])

(def ?PartialModule
  (make-recursively-optional ?Module))
