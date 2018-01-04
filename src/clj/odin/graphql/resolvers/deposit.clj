(ns odin.graphql.resolvers.deposit
  (:require [blueprints.models.security-deposit :as deposit]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))


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


(defn deposit-status
  [_ _ dep]
  (let [is-overdue (t/before? (t/now) (c/to-date-time (deposit/due dep)))]
    (cond
      (> (deposit/amount-pending dep) 0)                           :pending
      (= (deposit/amount dep) (deposit/amount-paid dep))           :paid
      (= (deposit/amount-paid dep) 0)                              :unpaid
      (> (deposit/amount-remaining dep) (deposit/amount-paid dep)) :partial
      (and is-overdue (> (deposit/amount-remaining dep) 0))        :overdue
      :otherwise                                                   :pending)))


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
   :deposit/status           deposit-status
   :deposit/refund-status    refund-status})
