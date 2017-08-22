(ns odin.profile.payments.sources.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [odin.l10n :as l10n]
            [toolbelt.core :as tb]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [odin.utils.formatters :as format]
            [odin.components.payments :as payments-ui]
            [odin.profile.payments.sources.mocks :as mocks]))


(defn source-list
  "A vertical menu listing the linked payment sources."
  [sources current-source]
  [:nav.panel.is-rounded
   (for [source sources]
     (let [{id    :id
            type  :type
            name  :name
            last4 :last4} source]
      ^{:key id}
      [:a.panel-block {:class    (when (= id (get current-source :id)) "is-active")
                       :on-click #(dispatch [:payment.sources/set-current source])}
       [:span.panel-icon
        (payments-ui/payment-source-icon (or type :bank))]
       [:span.flexrow.full-width
        [:span name]
        [:span.flex-pin-right (str "**** " last4)]]]))])



(defn source-detail
  "Display information about the currently-selected payment source."
  [source]
  [:div.card
   [:div.card-content
    [:div.flexrow
     [payments-ui/payment-source-icon (get source :type)]
     [:h3 (str (get source :name) " **** " (get source :last4))]]]
     ;;[:div.flex-pin-right
      ;;[:p "Added on June 28, 2017"]]]]

   [:footer.card-footer
    [:a.card-footer-item {:class "is-success"}
     [:span.icon.is-small [:i.fa.fa-check-circle]]
     [:span "Autopay On"]]
    [:a.card-footer-item "Edit"]
    [:a.card-footer-item.is-danger "Unlink"]]])


(defn source-payment-history
  "Display the transaction history for a given payment source."
  [source]
  (let [{txs  :payments
         name :name} source]
    (tb/log txs)
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


(defn modal-add-account [is-visible?]
  [ant/modal {:title     (l10n/translate :btn-add-new-account)
              :width     "640px"
              :visible   @is-visible?
              :on-ok     #(reset! is-visible? false)
              :on-cancel #(reset! is-visible? false)
              :footer    [(r/as-element [:a.button.is-success "Add Account"])]}
   "Add a thing"])


(defn add-new-source-button
  "Button for adding a new Payment Source."
  [modal-shown?]
  [:nav.panel.space-top.is-rounded
   [:a.panel-block {:on-click #(swap! modal-shown? not)}
    [:span.panel-icon
     [:span.icon [:i.fa.fa-plus-square-o]]]
    (l10n/translate :btn-add-new-account)
    [modal-add-account modal-shown?]]])



(defn sources []
  (let [accounts             (subscribe [:payment-sources])
        current              (subscribe [:payment-sources/current])
        loading              (subscribe [:payment-sources.list/loading?])
        add-account-visible? (r/atom false)]
    [:div
     [:h1 (l10n/translate :payment-sources)]
     [:div.columns
      [:div.column.is-4
       ;; Accounts List
       [source-list @accounts @current]
       [add-new-source-button add-account-visible?]]

      [:div.column.is-8
       ;; Details for selected Source
       [source-detail @current]
       [source-payment-history @current]]]]))
      ;; [modal-confirm-disable-autopay]]])
