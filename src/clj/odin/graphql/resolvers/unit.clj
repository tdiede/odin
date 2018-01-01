(ns odin.graphql.resolvers.unit
  (:require [blueprints.models.unit :as unit]
            [clojure.string :as string]
            [toolbelt.core :as tb]
            [blueprints.models.property :as property]
            [datomic.api :as d]))


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


(defn query
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
