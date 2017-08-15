(ns odin.profile.payments.sources.views
  (:require [odin.l10n :as l10n]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.profile.payments.sources.mocks :as mocks]))


(defn source-list
  "A vertical menu listing the linked payment sources."
  [sources]
  [:nav.panel.space-top
   (for [source sources]
     ^{:key (get source :id)}
     [:a.panel-block
      [:span.panel-icon
       (payments-ui/payment-source-icon (get source :type :bank))]
      [:span.flexrow.full-width
       [:span (get source :name)]
       [:span.flex-pin-right (str "**** " (get source :trailing-digits))]]])])


(defn source-detail
  "Display information about the currently-selected payment source."
  [source]
  [ant/card
   [:div.flexrow
    [payments-ui/payment-source-icon (get source :type)]
    [:h3 (str (get source :name) " **** " (get source :trailing-digits))]
    [:div.flex-pin-right
     [ant/button (l10n/translate :btn-unlink-account)]]]
   [:label.checkbox
    [:input {:type "checkbox" :checked "checked"}]
    (l10n/translate :use-for-autopay)]])


(defn source-payment-history
  "Display the transaction history for a given payment source."
  [current-source]
  (let [txs (get current-source :tx-history)]
   [ant/card
    [:h4 (l10n/translate :payment-history)]
    [payments-ui/payments-list txs]
    [payments-ui/payments-table txs]]))


(defn modal-confirm-disable-autopay []
  [:div.modal.is-active
   [:div.modal-background]
   [:div.modal-card
    [:header.modal-card-head
     [:h3 (l10n/translate :confirm-unlink-autopay)]]
    [:section.modal-card-body
     [:p "Autopay automatically transfers your rent each month, one day before your Due Date. We recommend enabling this feature, so you never need to worry about making rent on time."]]
    [:footer.modal-card-foot]]
   [:button.modal-close.is-large]])


(defn sources []
  [:div
   [:h1 "Payment Sources"]
   [:div.columns
    [:div.column.is-4
     [source-list mocks/payment-sources]]
    [:div.column
     [source-detail (first mocks/payment-sources)]
     [source-payment-history (first mocks/payment-sources)]]]])
    ; [modal-confirm-disable-autopay]])
