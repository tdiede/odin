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
    [:div
     ; [:h4 "Outstanding charges"]
     [:p "We've encountered an issue with your primary payment method. Want to " [:a "fix that?"]]]
    :warning false]

   [notification/page-notification
    [:div
     ; [:h4 "Rent overdue"]
     [:p "Your rent of $1,200 was due on August 1st. Want to " [:a "pay that now?"]]]
    :danger false]

   [notification/page-notification
    [:div
     [:p "Everything looks good! You paid your rent 3 days early this month. "
      [:a {:href "https://imgur.com/a/gwpjZ" :target "_blank"}"Here's a cool meme"] " to celebrate."]]
    :success]

   [ant/card {:class "is-flush"}
    [payments-ui/payments-table mocks/transaction-history]]])
