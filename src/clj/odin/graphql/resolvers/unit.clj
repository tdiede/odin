(ns odin.graphql.resolvers.unit
  (:require [blueprints.models.license :as license]
            [blueprints.models.property :as property]
            [blueprints.models.unit :as unit]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn number
  "Room number of this room."
  [_ _ unit]
  (-> (unit/code unit)
      (string/split #"-")
      (last)
      (tb/str->int)))


(defn occupant
  [{conn :conn} _ unit]
  (unit/occupied-by (d/db conn) unit))


(defn- available? [license-price]
  (not (false? (get-in license-price [:license-price/license :license/available]))))


;; ==============================================================================
;; queries ======================================================================
;; ==============================================================================


(defn query
  "Query units based on `params`."
  [{conn :conn} {params :params} _]
  (if-let [p (:property params)]
    (property/units (d/entity (d/db conn) p))
    (->> (d/q '[:find [?u ...]
                :where
                [?u :unit/code _]]
              (d/db conn))
         (map (partial d/entity (d/db conn))))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;; fields
   :unit/number   number
   :unit/occupant occupant
   ;; queries
   :unit/query    query})
