(ns odin.graphql.resolvers.deposit
  (:require [blueprints.models.security-deposit :as deposit]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn amount-remaining
  [_ _ deposit]
  (deposit/amount-remaining deposit))


(defn amount-paid
  [_ _ deposit]
  (deposit/amount-paid deposit))


(defn amount-pending
  [_ _ deposit]
  (deposit/amount-pending deposit))


(defn refund-status
  [_ _ deposit]
  (when-let [s (deposit/refund-status deposit)]
    (keyword (name s))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;;fields
   :deposit/amount-remaining amount-remaining
   :deposit/amount-paid      amount-paid
   :deposit/amount-pending   amount-pending
   :deposit/refund-status    refund-status})
