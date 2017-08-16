(ns odin.profile.payments.sources.mocks
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [odin.utils.formatters :refer [str->timestamp]]
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
    :status            :payment.status/pending
    :for               :payment.for/rent
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (str->timestamp "Jul 29, 2017")
    :period-start-date (str->timestamp "Aug 1, 2017")
    :period-end-date   (str->timestamp "Aug 31, 2017")
    :due               (str->timestamp "Aug 1, 2017")}

   {:id                841498190978978984
    :status            :payment.status/paid
    :for               :payment.for/order
    :amount            59.99
    :method            :payment.method/stripe-charge
    :paid-on           (str->timestamp "Jul 17, 2017")
    :period-start-date nil
    :period-end-date   nil
    :due               (.getTime (js/Date.))}

   {:id                341498137822013984
    :status            :payment.status/paid
    :for               :payment.for/rent
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (str->timestamp "Jul 1, 2017")
    :period-start-date (str->timestamp "Jul 1, 2017")
    :period-end-date   (str->timestamp "Jul 31, 2017")
    :due               (str->timestamp "Jul 1, 2017")}

   {:id                20175742552222013984
    :status            :payment.status/paid
    :for               :payment.for/rent
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (str->timestamp "Jun 3, 2017")
    :period-start-date (str->timestamp "Jun 1, 2017")
    :period-end-date   (str->timestamp "Jun 30, 2017")
    :due               (str->timestamp "Jun 1, 2017")}

   {:id                201757425582013984
    :status            :payment.status/paid
    :for               :payment.for/deposit
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid-on           (str->timestamp "Jun 3, 2017")
    :period-start-time nil
    :period-end-time   nil
    :due               (str->timestamp "Jun 1, 2017")}])




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
