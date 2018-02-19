(ns member.profile.membership.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [iface.components.payments :as payments-ui]
            [iface.components.membership :as membership]
            [iface.utils.formatters :as format]
            [iface.utils.time :as t]
            [member.l10n :as l10n]
            [member.routes :as routes]
            [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn link-bank-tooltip-title []
  [:span "Please link a bank account in your "
   [:a {:href (routes/path-for :profile.payment/sources)}
    "Payment Methods"] " page."])


;; =============================================================================
;; Security Deposit
;; =============================================================================


(defn- card-title [type]
  [:h4.bold
   (case type
     :rent    "Rent"
     :deposit "Security Deposit"
     "")])


(defn- render-card-icon
  "Renders a large icon with title above it, colored according to payment status."
  [type status]
  [:div.flexcol.flex-center
   [:span.icon.is-large
    {:class (case status
              :overdue "text-red"
              :unpaid  "text-yellow"
              :partial "text-yellow"
              :pending "text-blue"
              :paid    "text-green"
              "")}
    [:i.fa.fa-3x {:class (case type
                           :rent    "fa-home"
                           :deposit "fa-shield"
                           "")}]]])


(defn- deposit-status
  [{:keys [amount amount_remaining amount_paid amount_pending due] :as deposit}]
  (let [is-overdue (t/is-before-now due)]
    (cond
      (> amount_pending 0)                    :pending
      (= amount amount_paid)                  :paid
      (and is-overdue (> amount_remaining 0)) :overdue
      (= amount_paid 0)                       :unpaid
      (> amount_remaining amount_paid)        :partial
      :otherwise                              :pending)))


(defmulti deposit-status-content (fn [deposit sources] (deposit-status deposit)))


(defmethod deposit-status-content :default [{:keys [amount]} sources]
  [:p.fs2 "Your security deposit has been paid in entirety."])


(defmethod deposit-status-content :overdue [{:keys [id amount due amount_remaining]} sources]
  [:div
   [:p.fs2 "Your security deposit is overdue."]
   [ant/tooltip
    {:title (when (empty? sources)
              (r/as-element [link-bank-tooltip-title]))}
    [ant/button
     {:type     :danger
      :size     :large
      :on-click #(dispatch [:modal/show id])
      :disabled (empty? sources)}
     (format/format "Pay remaining amount (%s)" (format/currency amount_remaining))]]])


(defmethod deposit-status-content :partial [{:keys [id amount_remaining]} sources]
  [:div
   [:p.fs2 "Your security deposit is partially paid."]
   [ant/tooltip
    {:title (when (empty? sources)
              (r/as-element [link-bank-tooltip-title]))}
    [ant/button
     {:on-click #(dispatch [:modal/show id])
      :size     :large
      :disabled (empty? sources)}
     (format/format "Pay Remaining (%s)" (format/currency amount_remaining))]]])


(defmethod deposit-status-content :unpaid [{:keys [id amount_remaining]} sources]
  [:div
   [:p.fs2 "Your security deposit is unpaid."]
   [ant/tooltip
    {:title (when (empty? sources)
              (r/as-element [link-bank-tooltip-title]))}
    [ant/button
     {:on-click #(dispatch [:modal/show id])
      :size     :large
      :disabled (empty? sources)}
     (format/format "Pay Now (%s)" (format/currency amount_remaining))]]])


(defmethod deposit-status-content :pending [{:keys [amount_pending]} _]
  [:p.fs2 "Your recent payment is currently pending, and should be completed shortly."])


(defn deposit-status-card []
  (let [loading (subscribe [:member.license/loading?])
        deposit (subscribe [:member/deposit])
        payment (subscribe [:member.deposit/payment])
        sources (subscribe [:payment.sources/verified-banks])
        paying  (subscribe [:ui/loading? :member/pay-deposit!])]
    (fn []
      [:div.mb2
       [ant/card {:loading @loading}
        [payments-ui/make-payment-modal @payment
         :on-confirm (fn [payment-id source-id _]
                       (dispatch [:member/pay-deposit! payment-id source-id]))
         :loading @paying
         :sources @sources]
        [:div.columns
         [:div.column.is-2
          (when (not @loading) [render-card-icon :deposit (deposit-status @deposit)])]
         [:div.column
          (card-title :deposit)
          (when (not (nil? @deposit))
            (deposit-status-content @deposit @sources))]]]])))


;; =============================================================================
;; Rent
;; =============================================================================

(defn- rent-overdue? [payment]
  (t/is-before-now (:due payment)))


(defn- rent-paid-card []
  (let [this-month (.format (js/moment.) "MMMM")]
    [ant/card
     [:div.columns
      [:div.column.is-2
       [render-card-icon :rent :paid]]
      [:div.column
       (card-title :rent)
       [:p.fs2 (format/format "Your rent is paid for the month of %s. Thanks!" this-month)]]]]))


(defn- rent-payment-text [{due :due :as payment}]
  (if (rent-overdue? payment)
    (format/format "Your rent payment was due on %s. Please pay it now to retain your membership."
                   (format/date-month-day due))
    (str "Your next rent payment is due by " (format/date-month-day due) ".")))


(defn rent-due-card
  "Renders an outstanding rent payment with the amount and a CTA to pay."
  [{:keys [id amount due pstart pend] :as payment}]
  (let [sources (subscribe [:payment.sources/verified-banks])
        paying  (subscribe [:ui/loading? :member/pay-rent-payment!])]
    (fn [{:keys [id amount due pstart pend late_fee] :as payment}]
      [ant/card
       [:div.columns
        [:div.column.is-2
         [render-card-icon :rent (if (rent-overdue? payment) :overdue :unpaid)]]
        [:div.column
         (when (some? @sources)
           [payments-ui/make-payment-modal payment
            :on-confirm (fn [payment-id source-id _]
                          (dispatch [:member/pay-rent-payment! payment-id source-id]))
            :loading @paying
            :sources @sources])
         (card-title :rent)
         [:p.fs2 (rent-payment-text payment)]
         [ant/tooltip
          {:title (cond
                    (empty? @sources) (r/as-element [link-bank-tooltip-title])
                    (> late_fee 0)    (format/format "A late fee of %s has been added."
                                                     (format/currency late_fee))
                    :otherwise        nil)}
          [ant/button
           {:type     (if (rent-overdue? payment) :danger :primary)
            :size     :large
            :on-click #(dispatch [:modal/show id])
            :disabled (empty? @sources)}
           (format/format "Pay Now (%s)" (format/currency (+ amount late_fee)))]]]]])))


(defn- rent-due-cards
  "Container card for rent payments, if they exist. Otherwise displays a 'youre all good' message."
  [payments]
  [:div
   (doall
    (map-indexed
     #(with-meta [rent-due-card %2] {:key %1})
     payments))])


(defn rent-status-card []
  (let [license  (subscribe [:member/license])
        loading  (subscribe [:member.license/loading?])
        payments (subscribe [:member/rent-payments {:status :due}])]
    [:div.mb2
     (cond
       @loading           [ant/card {:loading true}]
       (empty? @payments) [rent-paid-card]
       :otherwise         [rent-due-cards @payments])]))


;; =============================================================================
;; Membership + License
;; =============================================================================

(defn membership-summary []
  [:div
   [:h2 "Status"]
   [deposit-status-card]
   [rent-status-card]])


(defn membership []
  (let [license (subscribe [:member/license])
        loading (subscribe [:member.license/loading?])]
    [:div
     (typography/view-header
      (l10n/translate :membership)
      (when (nil? @license)
        "It looks like you're not a member, so nothing to see here."))

     (when (some? @license)
       [:div.columns
        [:div.column
         [membership-summary]]

        [:div.column.is-5
         [:h2 "Membership Agreement"]
         [membership/license-summary @license {:loading @loading}]]])]))
