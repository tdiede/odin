(ns odin.profile.payments.history.views
  (:require [odin.l10n :as l10n]
            [odin.components.notifications :as notification]
            [odin.profile.payments.sources.mocks :as mocks]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [odin.components.modals :as modal]
            [odin.components.payments :as payments-ui]))

(defn sample-modal-content []
  [:p "Here's some content for a modal."])


(defn history [param]
  (let [shown (r/atom false)]
   (fn []
     [:div
      [:h1 "Payment History"]

      [payments-ui/rent-overdue-notification (first mocks/transaction-history)]

      ; [notification/banner
      ;  [:div
      ;   ; [:h4 "Outstanding charges"]
      ;   [:p
      ;    "We've encountered an issue with your primary payment method. Want to "
      ;    [:a {:on-click #(swap! shown not)} "pay that now?"]
      ;    [ant/modal {:title "Modal title"
      ;                :visible @shown
      ;                :on-ok #(reset! shown false)
      ;                :on-cancel #(reset! shown false)}
      ;     [:p "Modal contents."]]]]
      ;  :warning false]
      ;
      ; [notification/banner
      ;  [:div
      ;   ; [:h4 "Rent overdue"]
      ;   [:p "Your rent of $1,200 was due on August 1st. Want to " [:a "pay that now?"]]]
      ;  :danger false]
      ;
      ; [notification/banner
      ;  [:div
      ;   [:p "Everything looks good! You paid your rent 3 days early this month. "
      ;    [:a {:href "https://imgur.com/a/gwpjZ" :target "_blank"}"Here's a cool meme"] " to celebrate."]]
      ;  :success]

      [ant/card {:class "is-flush"}
       [payments-ui/payments-table mocks/transaction-history]]])))
