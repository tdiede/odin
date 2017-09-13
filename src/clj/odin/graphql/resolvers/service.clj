(ns odin.graphql.resolvers.service
  (:require [blueprints.models.service :as service]))


(defn billed
  [_ _ service]
  (-> (service/billed service) name keyword))


(def resolvers
  {:service/billed billed})
