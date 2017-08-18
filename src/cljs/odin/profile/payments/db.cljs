(ns odin.profile.payments.db
  (:require [odin.utils.formatters :refer [str->timestamp]]))

(def transaction-history
  [{:id      111498190582013984
    :status  :payment.status/pending
    :for     :payment.for/rent
    :amount  1400.00
    :method  :payment.method/stripe-charge
    :paid_on (str->timestamp "Jul 29, 2017")
    :pstart  (str->timestamp "Aug 1, 2017")
    :pend    (str->timestamp "Aug 31, 2017")
    :due     (str->timestamp "Aug 1, 2017")}

   {:id      841498190978978984
    :status  :payment.status/paid
    :for     :payment.for/order
    :amount  59.99
    :method  :payment.method/stripe-charge
    :paid_on (str->timestamp "Jul 17, 2017")
    :pstart  nil
    :pend    nil
    :due     (.getTime (js/Date.))}

   {:id      341498137822013984
    :status  :payment.status/paid
    :for     :payment.for/rent
    :amount  1400.00
    :method  :payment.method/stripe-charge
    :paid_on (str->timestamp "Jul 1, 2017")
    :pstart  (str->timestamp "Jul 1, 2017")
    :pend    (str->timestamp "Jul 31, 2017")
    :due     (str->timestamp "Jul 1, 2017")}

   {:id      20175742552222013984
    :status  :payment.status/paid
    :for     :payment.for/rent
    :amount  1400.00
    :method  :payment.method/stripe-charge
    :paid_on (str->timestamp "Jun 3, 2017")
    :pstart  (str->timestamp "Jun 1, 2017")
    :pend    (str->timestamp "Jun 30, 2017")
    :due     (str->timestamp "Jun 1, 2017")}

   {:id                201757425582013984
    :status            :payment.status/paid
    :for               :payment.for/deposit
    :amount            1400.00
    :method            :payment.method/stripe-charge
    :paid_on           (str->timestamp "Jun 3, 2017")
    :period-start-time nil
    :period-end-time   nil
    :due               (str->timestamp "Jun 1, 2017")}])


(def path ::payments)

(def default-value
  {path {:payments []
         :loading  {:payments/list false}}})
