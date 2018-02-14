(ns odin.graphql.resolvers.property
  (:require [datomic.api :as d]
            [blueprints.models.property :as property]))


;; ==============================================================================
;; query resolvers===============================================================
;; ==============================================================================


(defn entry
  "Look up a single property by id."
  [{conn :conn} {id :id} _]
  (d/entity (d/db conn) id))


(defn query
  [{conn :conn} _ _]
  (->> (d/q '[:find [?e ...]
              :where
              [?e :property/code _]]
            (d/db conn))
       (map (partial d/entity (d/db conn)))))


;; ==============================================================================
;; resolvers ====================================================================
;; ==============================================================================


(def resolvers
  {;; fields
   ;; queries
   :property/entry entry
   :property/query query})
