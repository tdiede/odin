(ns odin.profile.payments.sources.views
  (:require [odin.l10n :as l10n]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.profile.payments.sources.mocks :as mocks]))


(defn source-list
  "A vertical menu listing the linked payment sources."
  [sources]
  [:nav.panel.space-top.is-rounded
   (for [source sources]
     ^{:key (get source :id)}
     [:a.panel-block
      [:span.panel-icon
       (payments-ui/payment-source-icon (get source :type :bank))]
      [:span.flexrow.full-width
       [:span (get source :name)]
       [:span.flex-pin-right (str "**** " (get source :trailing-digits))]]])])


(defn add-new-source-button
  "Button for adding a new Payment Source."
  []
  [:nav.panel.space-top.is-rounded
   [:a.panel-block
    [:span.panel-icon
     [:span.icon [:i.fa.fa-plus-square-o]]]
    "Add new source"]])



(defn source-detail
  "Display information about the currently-selected payment source."
  [source]
  [:div.card
   [:div.card-content
    [:div.flexrow
     [payments-ui/payment-source-icon (get source :type)]
     [:h3 (str (get source :name) " **** " (get source :trailing-digits))]
     [:div.flex-pin-right
      [:p "Added on June 28, 2017"]]]]

   [:footer.card-footer
    [:a.card-footer-item {:class "is-success"}
     [:span.icon.is-small [:i.fa.fa-check-circle]]
     [:span "Autopay On"]]
    [:a.card-footer-item "Edit"]
    [:a.card-footer-item.is-danger "Unlink"]]])


  ; [ant/card
  ;  [:div.flexrow
  ;   [payments-ui/payment-source-icon (get source :type)]
  ;   [:h3 (str (get source :name) " **** " (get source :trailing-digits))]
  ;   [:div.flex-pin-right
  ;    [ant/button (l10n/translate :btn-unlink-account)]]]
  ;  [ant/checkbox (l10n/translate :use-for-autopay)]])


(defn source-payment-history
  "Display the transaction history for a given payment source."
  [current-source]
  (let [txs  (get current-source :tx-history)
        name (get current-source :name)]
   [ant/card {:title (l10n/translate :payment-history-for name)
              :class "is-flush"}
    ; [:h4 (l10n/translate :payment-history)]
    ; [payments-ui/payments-list txs]
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
   [:h1 (l10n/translate :payment-sources)]
   [:div.columns
    [:div.column.is-4
     [source-list mocks/payment-sources]
     [add-new-source-button]]
    [:div.column.is-8
     [source-detail (first mocks/payment-sources)]
     [source-payment-history (first mocks/payment-sources)]]]])
    ; [modal-confirm-disable-autopay]]])
