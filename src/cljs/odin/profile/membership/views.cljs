(ns odin.profile.membership.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [odin.components.payments :as payments-ui]
            [odin.l10n :as l10n]
            [odin.utils.formatters :as format]
            [odin.utils.time :as t]
            [re-frame.core :refer [dispatch subscribe]]
            [toolbelt.core :as tb]
            [reagent.core :as r]
            [odin.routes :as routes]))


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


(defn- render-card-icon
  "Renders a large icon with title above it, colored according to payment status."
  [type status]
  [:div.flexcol.flex-center
   [:p.bold.mb0 (case type
                  :rent    "Rent"
                  :deposit "Deposit"
                  "")]
   [:span.icon.is-large
    {:class (case status
              :overdue "text-red"
              :unpaid  "text-yellow"
              :partial "text-yellow"
              :pending "text-green"
              :paid    "text-green"
              "")}
    [:i.fa {:class (case type
                     :rent    "fa-home"
                     :deposit "fa-shield"
                     "")}]]])


(defn- deposit-status
  [{:keys [amount amount_remaining amount_paid amount_pending due]}]
  (let [is-overdue (t/is-before-now due)]
    (cond
      (= amount amount_paid)             :paid
      (and is-overdue (= amount_paid 0)) :overdue
      (= amount_paid 0)                  :unpaid
      (> amount_remaining amount_paid)   :partial
      :otherwise                         :pending)))


(defmulti deposit-status-content (fn [deposit sources] (deposit-status deposit)))


(defmethod deposit-status-content :default [{:keys [amount]} sources]
  [:div
   [:h4 "Your security deposit is paid. Nothing to do here!"]])


(defmethod deposit-status-content :partial [{:keys [id amount_remaining]} sources]
  [:div
   [:h4 "Security deposit partially paid."]
   [ant/tooltip
    {:title (when (empty? sources)
              (r/as-element [link-bank-tooltip-title]))}
    [ant/button
     {:on-click #(dispatch [:modal/show id])
      :disabled (empty? sources)}
     (format/format "Pay remaining amount (%s)" (format/currency amount_remaining))]]])


(defmethod deposit-status-content :pending [{:keys [amount_pending]} _]
  [:div
   [:h4 (str "Your recent payment is currently pending, and should be completed shortly.")]])


(defn deposit-status-card []
  (let [loading (subscribe [:member.license/loading?])
        deposit (subscribe [:member/deposit])
        payment (subscribe [:member.deposit/payment])
        sources (subscribe [:payment.sources/verified-banks])
        paying  (subscribe [:loading? :member/pay-deposit!])]
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
          (when (not (nil? @deposit))
            (deposit-status-content @deposit @sources))]]]])))


;; =============================================================================
;; Rent
;; =============================================================================


(defn- rent-paid-card []
  (let [this-month (.format (js/moment.) "MMMM")]
    [ant/card
     [:div.columns
      [:div.column.is-2
       [render-card-icon :rent :paid]]
      [:div.column
       [:h4 "Rent is paid."]
       [:p (format/format "Congratulations! You're paid for the month of %s." this-month)]]]]))


(defn rent-due-card
  "Renders an outstanding rent payment with the amount and a CTA to pay."
  [{:keys [id amount due pstart pend] :as payment}]
  (let [sources (subscribe [:payment.sources/verified-banks])
        paying  (subscribe [:loading? :member/pay-rent-payment!])]
    (fn [{:keys [id amount due pstart pend] :as payment}]
      [ant/card
       [:div.columns
        [:div.column.is-2
         [render-card-icon :rent :unpaid]]
        [:div.column
         (when (some? @sources)
           [payments-ui/make-payment-modal payment
            :on-confirm (fn [payment-id source-id _]
                          (dispatch [:member/pay-rent-payment! payment-id source-id]))
            :loading @paying
            :sources @sources])
         [:p (str "Your next rent payment is due by " (format/date-month-day due) ".")]
         [ant/tooltip
          {:title (when (empty? @sources)
                    (r/as-element [link-bank-tooltip-title]))}
          [ant/button
           {:type     "primary"
            :on-click #(dispatch [:modal/show id])
            :disabled (empty? @sources)}
           (format/format "Pay Now (%s)" (format/currency amount))]]]]])))


(defn- rent-due-cards
  "Container card for rent payments, if they exist. Otherwise displays a 'youre all good' message."
  [payments]
  (r/create-class
   {:component-will-mount
    (fn [_]
      (dispatch [:payment.sources/fetch]))
    :reagent-render
    (fn [payments]
      [:div
       (doall
        (map-indexed
         #(with-meta [rent-due-card %2] {:key %1})
         payments))])}))


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


(defn card-license-summary []
  (let [license (subscribe [:member/license])
        loading (subscribe [:member.license/loading?])
        {:keys [term rate starts ends property unit]} @license]
    [ant/card {:loading @loading
               :class   "is-flush"}
     (when-not (nil? rate)
       [ant/card {:loading @loading :class "is-flush"}
        [:div.card-image
         [:figure.image
          [:img {:src (:cover_image_url property)}]]]
        [:div.card-content
         [:div.content
          [:h3.title.is-4 (str (:name property) " #" (:number unit))]
          [:h4 (str term " months • " (str (format/date-short starts) " - " (format/date-short ends)))]
          [:p (str (format/currency rate) "/mo.")]]]])]))
;; If a link to view PDF version of license is provided, show it here
;;(when-not (nil? (:view-link @license))
;;[:footer.card-footer
;; [:a.card-footer-item
;;  [:span.icon.is-small [:i.fa.fa-file-text]]
;;  [:span.with-icon "View Agreement"]]]])]))


(defn membership-summary []
  [:div
   [:h2 "Status"]
   [deposit-status-card]
   [rent-status-card]])


(defn membership []
  [:div
   (typography/view-header
    (l10n/translate :membership))

   [:div.columns
    [:div.column
     [membership-summary]]

    [:div.column.is-5
     [:h2 "Rental Agreement"]
     [card-license-summary]]]])
