(ns odin.profile.payments.history.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [antizer.reagent :as ant]
            [odin.l10n :as l10n]
            [odin.components.ui :as ui]
            [odin.components.payments :as payments-ui]
            [toolbelt.core :as tb]))


(defn sample-modal-content []
  [:p "Here's some content for a modal."])


(defn history [param]
  (let [payments (subscribe [:payments])
        loading  (subscribe [:payments.list/loading?])]
    ;;(tb/log (map :amount @payments))
    [:div
     [:div.view-header
      [:h1 "Payment History"]
      [:p "All of your payment activity will appear here."]]

      ;;(when-some [py (first @payments)]
      ;;  [payments-ui/rent-overdue-notification py])]

     [ant/card {:class "is-flush"}
      [payments-ui/payments-table @payments @loading]]]))
