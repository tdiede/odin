(ns odin.profile.payments.sources.views
  (:require [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.components.ui :as ui]
            [odin.profile.payments.sources.views.forms :as forms]
            [odin.l10n :as l10n]
            [odin.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))


(defn source-list-item
  [{:keys [id type name last4] :as source}]
  (let [current (subscribe [:payment.sources/current])]
    [:a.panel-block {:class (when (= id (get @current :id)) "is-active")
                     :href  (routes/path-for :profile.payment/sources :query-params {:source-id id})}
     [:span.panel-icon
      (payments-ui/payment-source-icon (or type :bank))]
     [:span.flexrow.full-width
      [:span name]
      [:span.flex-pin-right (str "**** " last4)]]]))


(defn source-list
  "A vertical menu listing the linked payment sources."
  []
  (let [sources (subscribe [:payment/sources])
        ;; TODO: Show a loading state?
        ;; loading (subscribe [:payment.sources/loading?])
        ]
    [:nav.panel.is-rounded
     (doall
      (map-indexed
       #(with-meta [source-list-item %2] {:key %1})
       @sources))]))


(defn source-detail
  "Display information about the currently-selected payment source."
  []
  (let [{:keys [type name last4 autopay-on]} @(subscribe [:payment.sources/current])]
    [:div.card
     [:div.card-content
      [:div.flexrow
       ;; Source Name
       [payments-ui/payment-source-icon type]
       [:h3 (str name " **** " last4)]]]
     ;; Buttons
     [:footer.card-footer
      [:div.card-footer-item]
      (if autopay-on
        [:a.card-footer-item {:class "is-success"}
         [:span.icon.is-small [:i.fa.fa-check-circle]]
         [:span "Autopay On"]]
        [:a.card-footer-item [:span "Enable Autopay"]])
      [:a.card-footer-item.is-danger "Unlink"]]]))


(defn source-payment-history
  "Display the transaction history for a given payment source."
  []
  (let [{:keys [payments name]} @(subscribe [:payment.sources/current])
        is-loading              (subscribe [:payment.sources/loading?])]
    [ant/card {:title (l10n/translate :payment-history-for name)
               :class "is-flush"}
     [payments-ui/payments-table payments @is-loading]]))


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


(def ^:private tab-icon-classes
  {:bank    "fa-bank"
   :card    "fa-credit-card"
   :bitcoin "fa-bitcoin"})


(def ^:private tab-labels
  {:bank    "Bank Account"
   :card    "Credit Card"
   :bitcoin "Bitcoin"})


(defn form-tab [tab-type selected-type]
  [:li {:class (when (= selected-type tab-type) "is-active")}
   [:a {:on-click #(dispatch [:payment.sources.add/select-type tab-type])}
    [:span.icon.is-small [:i.fa {:class (get tab-icon-classes tab-type)}]]
    [:span (get tab-labels tab-type)]]])


;; NOTE: This is a simple example of how to componentize a common UI component.
(defn tabs []
  (let [this (r/current-component)]
    [:div.tabs (r/props this)
     (into [:ul] (r/children this))]))


(defn modal-add-source []
  (let [type         (subscribe [:payment.sources.add/type])
        is-visible   (subscribe [:modal/visible? :payment.source/add])
        source-types (subscribe [:payment.sources.add/available-types])]
    [ant/modal {:title     (l10n/translate :btn-add-new-account)
                :width     640
                :visible   @is-visible
                :on-ok     #(dispatch [:modal/hide :payment.source/add])
                :on-cancel #(dispatch [:modal/hide :payment.source/add])
                :footer    nil}
     [:div
      [tabs {:class "is-centered"}
       (doall
        (map-indexed
         #(with-meta [form-tab %2 @type] {:key %1})
         @source-types))]

      (case @type
        :bank    (r/as-element (ant/create-form (forms/bank-account)))
        :card    (r/as-element (ant/create-form (forms/credit-card)))
        :bitcoin [forms/bitcoin-account])]]))


(defn add-new-source-button
  "Button for adding a new Payment Source."
  []
  [:nav.panel.space-top.is-rounded
   [:a.panel-block {:on-click #(dispatch [:modal/show :payment.source/add])}
    [:span.panel-icon
     [:span.icon [:i.fa.fa-plus-square-o]]]
    (l10n/translate :btn-add-new-account)]])


(defn no-sources []
  [:div.box
   [:h3 "You don't have any accounts linked yet."]

   [:div.steps-vertical
    [ui/media-step "Link a payment source so you can settle your charges." "bank"]
    [ui/media-step "Turn on Autopay and never worry about a late payment again." "history"]
    [ui/media-step
     [:a.button.is-primary {:on-click #(dispatch [:modal/show :payment.source/add])}
      [:span.icon.is-small [:i.fa.fa-plus-square-o]]
      [:span (l10n/translate :btn-add-new-account)]]]]])


(defn sources []
  (let [sources (subscribe [:payment/sources])
        loading (subscribe [:payment.sources/loading?])]
    [:div
     [modal-add-source]
     [:div.view-header
      [:h1 (l10n/translate :payment-sources)]
      [:p "View and manage your payment sources."]]
     ;; TODO: If `@loading` is true, we shouldn't show empty components
     (if (empty? @sources)
       ;; Empty State
       [no-sources]

       ;; Show Sources
       [:div.columns
        [:div.column.is-4
         [source-list]
         [add-new-source-button]]

        [:div.column.is-8
         [source-detail]
         [source-payment-history]]])]))
