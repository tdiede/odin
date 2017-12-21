(ns odin.profile.payments.history.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [odin.components.payments :as payments-ui]
            [re-frame.core :refer [subscribe]]
            [toolbelt.core :as tb]))


(defn history [route]
  (let [payments (subscribe [:payments/by-account-id (get-in route [:requester :id])])
        loading  (subscribe [:loading? :payments/fetch])]
    [:div
     (typography/view-header
      "Payment History"
      "All of your payment activity will appear here.")

     [ant/card {:class "is-flush"}
      [payments-ui/payments-table @payments (and @loading (empty? @payments))]]]))
