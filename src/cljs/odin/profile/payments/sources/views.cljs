(ns odin.profile.payments.sources.views
  (:require [odin.views.content :as content]
            [antizer.reagent :as ant]
            [odin.components.subnav :refer [subnav]]))

(defn source-list
  "A vertical menu listing the linked payment sources."
  [sources]
  [:nav.panel
   [:p.panel-heading "Linked Accounts"]

   [:a.panel-block.is-active
    [:span.panel-icon
     [:i.fa.fa-university]]
    [:span.flexrow.full-width
     [:span "Wells Fargo"]
     [:span.flex-pin-right "**** 1234"]]]

   [:a.panel-block
    [:span.panel-icon
     [:i.fa.fa-cc-visa]]
    [:span.flexrow.full-width
     [:span "VISA"]
     [:span.flex-pin-right "**** 1234"]]]

   [:a.panel-block
    [:span.panel-icon
     [:i.fa.fa-cc-amex]]
    [:span.flexrow.full-width
     [:span "AmEx"]
     [:span.flex-pin-right "**** 1234"]]]])


(defn source-detail
  "Display information about the currently-selected payment source."
  [current-source]
  [ant/card
   [:div.flexrow
    [:h3 "Wells Fargo **** 1234"]
    [:div.flex-pin-right
     [ant/button "Unlink account"]]]
   [:label.checkbox
    [:input {:type "checkbox" :checked "checked"}]
    "Use this account for Autopay"]])


(defn source-payment-history
  "Display the transaction history for a given payment source."
  [current-source]
  [ant/card
   [:h4 "Payment History"]])


(defn modal-confirm-disable-autopay []
  [:div.modal.is-active
   [:div.modal-background]
   [:div.modal-card
    [:header.modal-card-head
     [:h3 "Turn off Autopay?"]]
    [:section.modal-card-body
     [:p "Autopay automatically transfers your rent each month, one day before your Due Date. We recommend enabling this feature, so you never need to worry about making rent on time."]]
    [:footer.modal-card-foot]]
   [:button.modal-close.is-large]])


(defmethod content/view :profile/payment-sources [route]
  [:div.columns
   [:div.column.is-2
    [subnav [["History" :profile/payment-history]
             ["Sources" :profile/payment-sources]]]]
   [:div.column.highlight-full
    [:h1 "Payment Sources"]
    [:div.columns
     [:div.column.is-4
      [source-list]]
     [:div.column
      [source-detail]
      [source-payment-history]]]]
   [modal-confirm-disable-autopay]])
