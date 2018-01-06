(ns odin.graphql.resolvers.property
  (:require [datomic.api :as d]))


(defn entry
  "Look up a single property by id."
  [{conn :conn} {id :id} _]
  (d/entity (d/db conn) id))


(def resolvers
  {;; fields
   ;; queries
   :property/entry entry})
