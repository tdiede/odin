(ns odin.graphql.resolvers.member-license
  (:require [blueprints.models.member-license :as member-license]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn status
  "The status of the member license."
  [_ _ license]
  (keyword (name (member-license/status license))))
