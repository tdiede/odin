(ns odin.graphql.resolvers.deposit
  (:require [blueprints.models.security-deposit :as deposit]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [teller.payment :as tpayment]
            [teller.customer :as tcustomer]))


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


(defn payments
  [{teller :teller} _ deposit]
  (let [customer (tcustomer/by-account teller (deposit/account deposit))]
    (tpayment/query teller {:payment-types [:payment.type/deposit]
                            :customers     [customer]})))


(defn deposit-status
  [_ _ dep]
  (let [is-overdue (t/after? (t/now) (c/to-date-time (deposit/due dep)))]
    (cond
      (> (deposit/amount-pending dep) 0)                    :pending
      (= (deposit/amount dep) (deposit/amount-paid dep))    :paid
      (and is-overdue (> (deposit/amount-remaining dep) 0)) :overdue
      (= (deposit/amount-paid dep) 0)                       :unpaid
      :otherwise                                            :partial)))


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
   :deposit/payments         payments
   :deposit/refund-status    refund-status
   :deposit/status           deposit-status})
