(ns odin.profile.payments.sources.mocks
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


;; :status
;; :payment.status/due
;; :payment.status/canceled
;; :payment.status/paid
;; :payment.status/pending
;; :payment.status/failed

;; :method
;; :payment.method/stripe-charge
;; :payment.method/stripe-invoice
;; :payment.method/check

;; :for
;; :payment.for/rent
;; :payment.for/deposit
;; :payment.for/order

(def transaction-history
  [{:id                111498190582013984
    :status            :payment.status/paid
    :for               :payment.for/rent
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (.getTime (js/Date.))
    :period-start-time (.getTime (js/Date.))
    :period-end-time   (.getTime (js/Date.))
    :due               (.getTime (js/Date.))}

   {:id                341498137822013984
    :status            :payment.status/pending
    :for               :payment.for/rent
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (.getTime (js/Date.))
    :period-start-time (.getTime (js/Date.))
    :period-end-time   (.getTime (js/Date.))
    :due               (.getTime (js/Date.))}

   {:id                201757425582013984
    :status            :payment.status/paid
    :for               :payment.for/deposit
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (.getTime (js/Date.))
    :period-start-time nil
    :period-end-time   nil
    :due               (.getTime (js/Date.))}

   {:id                841498190978978984
    :status            :payment.status/paid
    :for               :payment.for/order
    :amount            59.99
    :method            :payment.method/stripe-charge
    :paid-on           (.getTime (js/Date.))
    :period-start-time nil
    :period-end-time   nil
    :due               (.getTime (js/Date.))}])









(def payment-sources
  [{:id              193455612
    :type            :bank
    :name            "Wells Fargo"
    :trailing-digits 1234
    :tx-history      transaction-history}
   {:id              820980855
    :type            :visa
    :name            "VISA"
    :trailing-digits 4434
    :tx-history      transaction-history}
   {:id              326799135
    :type            :amex
    :name            "AmEx"
    :trailing-digits 6789
    :tx-history      transaction-history}])
