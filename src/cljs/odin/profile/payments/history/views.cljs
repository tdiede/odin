(ns odin.profile.payments.history.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [odin.components.payments :as payments-ui]
            [re-frame.core :refer [subscribe]]))

(defn sample-modal-content []
  [:p "Here's some content for a modal."])


(defn history [param]
  (let [payments (subscribe [:payments])
        loading  (subscribe [:payments.list/loading?])]
    [:div
     (typography/view-header
      "Payment History"
      "All of your payment activity will appear here.")

      ;;(when-some [py (first @payments)]
      ;;  [payments-ui/rent-overdue-notification py])]

     [ant/card {:class "is-flush"}
      [payments-ui/payments-table @payments @loading]]]))
