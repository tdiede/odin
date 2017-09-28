(ns odin.graphql.resolvers.member-license
  (:require [blueprints.models.member-license :as member-license]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn status
  "The status of the member license."
  [_ _ license]
  (keyword (name (member-license/status license))))


(defn autopay-on
  "Whether or not autopay is active for this license."
  [_ _ license]
  (keyword (name (member-license/autopay-on? license))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;;fields
   :member-license/status     status
   :member-license/autopay-on autopay-on})
