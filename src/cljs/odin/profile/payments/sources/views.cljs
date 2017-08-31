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

;; (defn source-list
;;   "A vertical menu listing the linked payment sources."
;;   [sources current-source]
;;   [:nav.panel.is-rounded
;;    (for [source sources]
;;      (let [{id    :id
;;             type  :type
;;             name  :name
;;             last4 :last4} source]
;;        ^{:key id}
;;        [:a.panel-block {:class (when (= id (get current-source :id)) "is-active")
;;                         :href  (routes/path-for :profile.payment/sources :query-params {:source-id id})}
;;         ;;:on-click #(dispatch [:payment.sources/set-current source])}
;;         [:span.panel-icon
;;          (payments-ui/payment-source-icon (or type :bank))]
;;         [:span.flexrow.full-width
;;          [:span name]
;;          [:span.flex-pin-right (str "**** " last4)]]]))])


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


;; (defn source-detail
;;   "Display information about the currently-selected payment source."
;;   [source]
;;   (let [{type :type
;;          name :name
;;          last4 :last4
;;          autopay-on :autopay} source]
;;     [:div.card
;;      [:div.card-content
;;       [:div.flexrow
;;        ;; Source Name
;;        [payments-ui/payment-source-icon type]
;;        [:h3 (str name " **** " last4)]]]
;;      ;; Buttons
;;      [:footer.card-footer
;;       [:div.card-footer-item]
;;       (if (= autopay-on true)
;;         [:a.card-footer-item {:class "is-success"}
;;          [:span.icon.is-small [:i.fa.fa-check-circle]]
;;          [:span "Autopay On"]]
;;         [:a.card-footer-item
;;          [:span "Enable Autopay"]])
;;       [:a.card-footer-item.is-danger "Unlink"]]]))


(defn source-payment-history
  "Display the transaction history for a given payment source."
  []
  (let [{:keys [payments name]} @(subscribe [:payment.sources/current])
        is-loading              (subscribe [:payment.sources/loading?])]
    [ant/card {:title (l10n/translate :payment-history-for name)
               :class "is-flush"}
     [payments-ui/payments-table payments @is-loading]]))


;; (defn source-payment-history
;;   "Display the transaction history for a given payment source."
;;   [source]
;;   (let [{txs  :payments
;;          name :name} source]
;;     [ant/card {:title (l10n/translate :payment-history-for name)
;;                :class "is-flush"}
;;      [payments-ui/payments-table txs]]))


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
        is-visible   (subscribe [:payment.sources.add/visible?])
        source-types (subscribe [:payment.sources.add/available-types])]
    [ant/modal {:title     (l10n/translate :btn-add-new-account)
                :width     640
                :visible   @is-visible
                :on-ok     #(dispatch [:payment.sources.add/hide])
                :on-cancel #(dispatch [:payment.sources.add/hide])
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


;; (defn modal-add-account []
;;   (let [account-type (subscribe [:payment-sources/new-account-type])]
;;     [ant/modal {:title     (l10n/translate :btn-add-new-account)
;;                 :width     "640px"
;;                 :visible   @is-visible?
;;                 :on-ok     #(reset! is-visible? false)
;;                 :on-cancel #(reset! is-visible? false)
;;                 :footer    nil}
;;      [:div
;;       [:div.tabs.is-centered
;;        [:ul
;;         [:li {:class (when (= @account-type :bank) "is-active")}
;;          [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :bank])}
;;           [:span.icon.is-small [:i.fa.fa-bank]]
;;           [:span "Bank Account"]]]
;;         [:li {:class (when (= @account-type :card) "is-active")}
;;          [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :card])}
;;           [:span.icon.is-small [:i.fa.fa-credit-card]]
;;           [:span "Credit Card"]]]
;;         [:li {:class (when (= @account-type :bitcoin) "is-active")}
;;          [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :bitcoin])}
;;           [:span.icon.is-small [:i.fa.fa-bitcoin]]
;;           [:span "Bitcoin"]]]]]
;;       (case @account-type
;;         :bank    (r/as-element (ant/create-form (form-add-new-bank-account is-visible?)))
;;         :card    (r/as-element (ant/create-form (form-add-new-credit-card is-visible?)))
;;         :bitcoin [form-add-bitcoin-account is-visible?])]]))


(defn add-new-source-button
  "Button for adding a new Payment Source."
  []
  (let [is-visible (subscribe [:payment.sources.add/visible?])]
    [:nav.panel.space-top.is-rounded
     [:a.panel-block {:on-click #(dispatch [:payment.sources.add/show])}
      [:span.panel-icon
       [:span.icon [:i.fa.fa-plus-square-o]]]
      (l10n/translate :btn-add-new-account)]]))


;; (defn add-new-source-button
;;   "Button for adding a new Payment Source."
;;   [modal-shown?]
;;   [:nav.panel.space-top.is-rounded
;;    [:a.panel-block {:on-click #(swap! modal-shown? not)}
;;     [:span.panel-icon
;;      [:span.icon [:i.fa.fa-plus-square-o]]]
;;     (l10n/translate :btn-add-new-account)
;;     [modal-add-account modal-shown?]]])


(defn no-sources []
  [:div.box
   [:h3 "You don't have any accounts linked yet."]

   [:div.steps-vertical
    [ui/media-step "Link a payment source so you can settle your charges." "bank"]
    [ui/media-step "Turn on Autopay and never worry about a late payment again." "history"]
    [ui/media-step
     [:a.button.is-primary {:on-click #(dispatch [:payment.sources.add/show])}
      [:span.icon.is-small [:i.fa.fa-plus-square-o]]
      [:span (l10n/translate :btn-add-new-account)]]]]])


;; (defn no-sources [modal-shown?]
;;   [:div
;;    [:div.box
;;     [:h3 "You don't have any accounts linked yet."]

;;     [:div.steps-vertical
;;      [ui/media-step "Link a payment source so you can settle your charges." "bank"]
;;      [ui/media-step "Turn on Autopay and never worry about a late payment again." "history"]
;;      [ui/media-step
;;       [:a.button.is-primary {:on-click #(swap! modal-shown? not)}
;;        [:span.icon.is-small [:i.fa.fa-plus-square-o]]
;;        [:span (l10n/translate :btn-add-new-account)]]]]]])


;;; On Modals & State

;; One of the things that gets annoying about the use of `r/atom` is that, while
;; it's an easy solution at the start of an implementation, it rapidly
;; complicates your code due to the necessity of state management. This is
;; precisely the problem that re-frame subscriptions and events solve.

;; We're also going to take advantage of the fact that *it doesn't matter where
;; in our html the modal itself is rendered*. We'll render it at the top of the
;; `sources` component, and not worry about rendering it in sub-components.


;;; Why were there sometimes two modals?

;; You were rendering the modal itself (`[modal-add-account modal-shown?]`) in two places:
;;  - within `sources`
;;  - within `add-new-source-button`

;; We can fix this by simply rendering the modal once: whenever `sources`, which
;; is top-level, is loaded. Because we're now using subscriptions and events,
;; this pattern is even easier.


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


;; (defn sources []
;;   (let [sources              (subscribe [:payment-sources])
;;         current-source       (subscribe [:payment-sources/current])
;;         loading              (subscribe [:payment-sources.list/loading?])
;;         modal-add-source?    (r/atom false)
;;         modal-remove-source? (r/atom false)
;;         modal-disable-autop? (r/atom false)]
;;     [:div
;;      [:div.view-header
;;       [:h1 (l10n/translate :payment-sources)]
;;       [:p "View and manage your payment sources."]]
;;      (if (empty? @sources)
;;        ;; Empty State
;;        [no-sources modal-add-source?]

;;        ;; Show Sources
;;        [:div.columns
;;         [:div.column.is-4
;;          [source-list @sources @current-source]
;;          [add-new-source-button modal-add-source?]]

;;         [:div.column.is-8
;;          [source-detail @current-source]
;;          [source-payment-history @current-source]]])
;;          ;;[ant/card
;;          ;; "Unverified"
;;          ;; [:a.button.is-primary "Verify Now"]]]]

;;      [modal-add-account modal-add-source?]]))
