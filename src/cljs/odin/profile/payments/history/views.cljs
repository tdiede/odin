(ns odin.profile.payments.history.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [toolbelt.core :as tb]))


(defn sample-modal-content []
  [:p "Here's some content for a modal."])


(defn history [param]
  (let [payments (subscribe [:payments])
        loading  (subscribe [:payments.list/loading?])]
    [:div
     [:div.view-header
      [:h1 "Payment History"]
      [:p "All of your payment activity will appear here."]]

      ;;(when-some [py (first @payments)]
      ;;  [payments-ui/rent-overdue-notification py])]

     ;;(tb/log @payments)

     [ant/card {:class "is-flush"}
      [payments-ui/payments-table @payments @loading]]]))
