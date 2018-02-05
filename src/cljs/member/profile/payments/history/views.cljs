(ns member.profile.payments.history.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [iface.components.payments :as payments]
            [re-frame.core :refer [subscribe]]
            [toolbelt.core :as tb]))


(defn history [route]
  (let [payments (subscribe [:payments/by-account-id (get-in route [:requester :id])])
        loading  (subscribe [:ui/loading? :payments/fetch])]
    [:div
     (typography/view-header
      "Payment History"
      "All of your payment activity will appear here.")

     [ant/card {:class "is-flush"}
      [payments/payments-table @payments (and @loading (empty? @payments))]]]))
