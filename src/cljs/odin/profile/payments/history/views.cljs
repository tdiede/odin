(ns odin.profile.payments.history.views
  (:require [odin.l10n :as l10n]
            [odin.components.notifications :as notification]
            [odin.profile.payments.sources.mocks :as mocks]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]))


(defn history []
  [:div
   [:h1 "Payment History"]
   [notification/page-notification
    [:p "Your rent's late, brah. It's gonna run you about 1,200 buckaroos. Want to "
        [:a "pay that now?"]]
    :danger]
   [:div.columns
    [:div.column
     [ant/card {:title "Rent Payments"
                :class "is-flush"}
      [payments-ui/payments-list mocks/transaction-history]]]
    [:div.column
     [ant/card {:title "Service Orders"
                :class "is-flush"}
      [payments-ui/payments-list mocks/transaction-history]]]]])
