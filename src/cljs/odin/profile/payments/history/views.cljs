(ns odin.profile.payments.history.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]))


(defn sample-modal-content []
  [:p "Here's some content for a modal."])


(defn history [param]
  (let [payments (subscribe [:payments])]
    [:div
     [:h1 "Payment History"]

     (when-some [py (first @payments)]
       [payments-ui/rent-overdue-notification py])

     [ant/card {:class "is-flush"}
      [payments-ui/payments-table @payments]]]))
