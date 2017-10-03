(ns odin.profile.payments.sources.views
  (:require [antizer.reagent :as ant]
            [iface.media :as media]
            [iface.tooltip :as tooltip]
            [iface.typography :as typography]
            [odin.components.payments :as payments-ui]
            [odin.l10n :as l10n]
            [odin.profile.payments.sources.views.forms :as forms]
            [odin.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))


(defn- is-unverified [{:keys [type status] :as source}]
  (and (= type :bank) (not= status "verified")))


(defn- is-default [{:keys [type default]}]
  (and (= type :card) (true? default)))


(defn account-digits
  [{:keys [type last4]}]
  (case type
    :card (str "xxxxxxxxxxxx" last4)
    (str "xxxxxx" last4)))


(defn add-new-source-button
  "Button for adding a new Payment Source."
  []
  [ant/button {:size     :large
               :icon     "plus-circle-o"
               :on-click #(dispatch [:modal/show :payment.source/add])}
   (l10n/translate :btn-add-new-account)])


(defn source-list-item
  [{:keys [id type name last4 default autopay] :as source}]
  (let [current (subscribe [:payment.sources/current])]
    [:a.source-list-item
     {:class (when (= id (:id @current)) "is-active")
      :href  (routes/path-for :profile.payment/sources :query-params {:source-id id})}

     [:div.source-list-item-info
      [:p.bold name]
      [:p (account-digits source)]
      (when (= type :card) [:span (:expires source)])

      [:div.source-item-end
       (if (is-unverified source)
         [:p.italic
          [ant/icon {:type "exclamation-circle" :style {:font-size ".8rem"}}]
          [:span.fs3 {:style {:margin-left 4}} "Unverified"]]
         [:span
          (payments-ui/payment-source-icon (or type :bank))
          (when autopay [ant/icon {:type :sync}])
          (when default [ant/icon {:type :check-circle}])])]]]))


(defn source-list
  "A vertical menu listing the linked payment sources."
  []
  (let [sources (subscribe [:payment/sources])]
    [:div.source-list
     (doall
      (map-indexed
       #(with-meta [source-list-item %2] {:key %1})
       @sources))
     [add-new-source-button]]))


(defn- show-cannot-remove-bank [source]
  (ant/modal-warning
   {:title   (r/as-element
              [:span.title.is-5 (str "Cannot remove " (:name source))])
    :width   640
    :ok-text "OK, got it."
    :content (r/as-element
              [:div
               [:p "You must have a bank account linked in order to pay rent."]
               [:p "If you wish to remove this bank, please link another one first."]])}))


(defn- source-actions-menu []
  (let [source     (subscribe [:payment.sources/current])
        can-remove (subscribe [:payment.sources.current/can-remove?])]
    [ant/menu
     [ant/menu-item {:key "removeit"}
      [:a.text-red
       {:href     "#"
        :on-click #(if @can-remove
                     (dispatch [:modal/show :payment.source/remove])
                     (show-cannot-remove-bank @source))}
       "Remove this account"]]]))


(defn source-detail
  "Display information about the currently-selected payment source."
  []
  (let [{:keys [type name] :as source} @(subscribe [:payment.sources/current])]
    [ant/card {:class "mb2"}
     [:div.flexrow.align-start
      [payments-ui/payment-source-icon type]
      [:div.ml1
       [:h3.lh13 name]
       [:p (account-digits source)]]]

     [:div.card-controls
      [ant/dropdown {:trigger ["click"]
                     :overlay (r/as-element [source-actions-menu])}
       [:a.ant-dropdown-link
        [:span "More"]
        [ant/icon {:type "down"}]]]]

     [:div.mt2
      (when (is-unverified source)
        [ant/button {:type     "primary"
                     :on-click #(dispatch [:modal/show :payment.source/verify-account])}
         [ant/icon {:type "check-circle"}]
         [:span "Verify Account"]])]]))


(defn source-payment-history
  "Display the transaction history for a given payment source."
  []
  (let [{:keys [payments name]} @(subscribe [:payment.sources/current])]
    [ant/card {:class "is-flush stripe-style"}
     [payments-ui/payments-table payments false]]))


(defn bank-radio-option
  [{:keys [id name last4] :as bank}]
  [ant/radio {:value id} (str name " " (account-digits bank))])


(defn modal-enable-autopay-footer [selected-autopay-source]
  (let [is-submitting (subscribe [:loading? :payment.sources.autopay/enable!])]
    [:div
     [ant/button
      {:on-click #(dispatch [:modal/hide :payment.source/autopay-enable])
       :size     :large}
      "I'd rather pay manually."]
     [ant/button {:type     "primary"
                  :size     :large
                  :loading  @is-submitting
                  :on-click #(dispatch [:payment.sources.autopay/enable! selected-autopay-source])}
      "Great! Let's do it"]]))


(defn modal-confirm-enable-autopay []
  (let [is-visible (subscribe [:modal/visible? :payment.source/autopay-enable])
        banks      (subscribe [:payment/sources :bank])
        selected   (r/atom (-> @banks first :id))]
    [ant/modal {:title     "Autopay your rent?"
                :visible   @is-visible
                :on-cancel #(dispatch [:modal/hide :payment.source/autopay-enable])
                :footer    (r/as-element [modal-enable-autopay-footer @selected])}
     [:div
      [:p "Autopay automatically transfers your rent payment each month. We
          recommend enabling this feature, so you never need to worry about
          making rent on time."]
      [:p.bold "Choose a bank account to use for Autopay:"]
      [ant/radio-group {:default-value @selected
                        :disabled      (< (count @banks) 2)
                        :on-change     #(reset! selected (.. % -target -value))}
       (map-indexed
        (fn [idx {key :key :as item}]
          (-> (bank-radio-option item)
              (with-meta {:key idx})))
        @banks)]]]))



(defn modal-disable-autopay-footer [selected-autopay-source]
  (let [is-submitting (subscribe [:loading? :payment.sources.autopay/disable!])]
    [:div
     [ant/button {:on-click #(dispatch [:modal/hide :payment.source/autopay-disable])} "Cancel"]
     [ant/button {:type     "primary"
                  :loading  @is-submitting
                  :on-click #(dispatch [:payment.sources.autopay/disable! selected-autopay-source])}
      "Disable Autopay"]]))


(defn modal-confirm-disable-autopay []
  (let [is-visible     (subscribe [:modal/visible? :payment.source/autopay-disable])
        autopay-source (subscribe [:payment.sources/autopay-source])]
    [ant/modal {:title     (l10n/translate :confirm-unlink-autopay)
                :visible   @is-visible
                :footer    (r/as-element [modal-disable-autopay-footer (:id @autopay-source)])
                :on-ok     #(dispatch [:payment.sources.autopay/disable! (:id @autopay-source)])
                :on-cancel #(dispatch [:modal/hide :payment.source/autopay-disable])}
     [:div
      [:p "Autopay automatically transfers your rent each month, one day before your Due Date. We recommend enabling this feature, so you never need to worry about making rent on time."]]]))


(defn modal-verify-account-footer []
  (let [current-id    (subscribe [:payment.sources/current-id])
        is-submitting (subscribe [:loading? :payment.sources.bank/verify!])
        amounts       (subscribe [:payment.sources.bank.verify/microdeposits])
        amount-1      (:amount-1 @amounts)
        amount-2      (:amount-2 @amounts)]
    [:div
     [ant/button {:on-click #(dispatch [:modal/hide :payment.source/verify-account])} "Cancel"]
     [ant/button {:type     "primary"
                  :loading  @is-submitting
                  :on-click #(dispatch [:payment.sources.bank/verify! @current-id amount-1 amount-2])}
      "Verify Amounts"]]))


(defn modal-verify-account []
  (let [bank       (subscribe [:payment.sources/current])
        amounts    (subscribe [:payment.sources.bank.verify/microdeposits])
        is-visible (subscribe [:modal/visible? :payment.source/verify-account])]
    [ant/modal {:title   (str "Verify " (:name @bank))
                :visible @is-visible
                :footer  (r/as-element [modal-verify-account-footer])}
     [:div
      [:p "If the two microdeposits have posted to your account, enter them below to verify ownership."]
      [:p.fs2 "Note: Amounts should be entered in " [:i "cents"] " (e.g. '32' not '0.32')"]
      [:form.form-verify-microdeposits.mt2 {:on-submit #(.preventDefault %)}
       [ant/input-number {:default-value (:amount-1 @amounts)
                          :min           1
                          :max           99
                          :placeholder   "00"
                          :size          "large"
                          :on-change     #(dispatch [:payment.sources.bank.verify/edit-amount :amount-1 %])}]
       [ant/input-number {:default-value (:amount-2 @amounts)
                          :min           1
                          :max           99
                          :placeholder   "00"
                          :size          "large"
                          :on-change     #(dispatch [:payment.sources.bank.verify/edit-amount :amount-2 %])}]]]]))


(defn modal-confirm-remove-account []
  (let [is-visible     (subscribe [:modal/visible? :payment.source/remove])
        removing       (subscribe [:loading? :payment.source/delete!])
        current-source (subscribe [:payment.sources/current])]
    (fn []
      [ant/modal {:title     "Remove this account?"
                  :width     640
                  :visible   @is-visible
                  :ok-text   "Yes, remove"
                  :on-cancel #(dispatch [:modal/hide :payment.source/remove])
                  :footer    [(r/as-element
                               ^{:key "cancel"}
                               [ant/button
                                {:type     :ghost
                                 :on-click #(dispatch [:modal/hide :payment.source/remove])}
                                "Cancel"])
                              (r/as-element
                               ^{:key "delete"}
                               [ant/button
                                {:type     :primary
                                 :loading  @removing
                                 :on-click #(dispatch [:payment.source/delete! (:id @current-source)])}
                                "Yes, remove it."])]}
       [:p "If you remove this account, it will no longer be available for settling payments."]])))


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


(defn tabs []
  (let [this (r/current-component)]
    [:div.tabs (r/props this)
     (into [:ul] (r/children this))]))


(defn modal-add-source []
  (let [type         (subscribe [:payment.sources.add/type])
        is-visible   (subscribe [:modal/visible? :payment.source/add])
        source-types (subscribe [:payment.sources.add/available-types])]
    (r/create-class
     {:component-will-mount
      (fn [_]
        (dispatch [:stripe/load-scripts "v2"])
        (dispatch [:stripe/load-scripts "v3"]))
      :reagent-render
      (fn []
        [ant/modal {:title     (l10n/translate :btn-add-new-account)
                    :width     640
                    :visible   @is-visible
                    :on-ok     #(dispatch [:modal/hide :payment.source/add])
                    :on-cancel #(dispatch [:modal/hide :payment.source/add])
                    :footer    nil}
         [:div
          (when [:div.loading-box])
          [tabs {:class "is-centered"}
           (doall
            (map-indexed
             #(with-meta [form-tab %2 @type] {:key %1})
             @source-types))]

          (case @type
            :bank    (r/as-element (ant/create-form (forms/bank-account)))
            :card    (r/as-element (ant/create-form (forms/credit-card)))
            :bitcoin [forms/bitcoin-account])]])})))


(defn no-sources []
  [:div.box
   [:h3 "You don't have any accounts linked yet."]

   [:div.steps-vertical
    [media/step "Link a payment source so you can settle your charges." "bank"]
    [media/step "Turn on Autopay and never worry about a late payment again." "history"]
    [media/step
     [ant/button {:type     :primary
                  :size     :large
                  :on-click #(dispatch [:modal/show :payment.source/add])}
      [:span.icon.is-small [:i.fa.fa-plus-square-o]]
      [:span (l10n/translate :btn-add-new-account)]]]]])


(defn- autopay-tooltip []
  (let [has-verified (subscribe [:payment.sources/has-verified-bank?])
        rent-unpaid  (subscribe [:member.rent/unpaid?])
        this         (r/current-component)]
    [ant/tooltip
     {:title (cond
               (not @has-verified) "To enable Autopay, you must first add and verify a bank account."
               @rent-unpaid        "You must pay your outstanding rent payment before enabling Autopay."
               :otherwise          nil)}
     (into [:span] (r/children this))]))


(defn source-settings []
  (let [autopay-on      (subscribe [:payment.sources/autopay-on?])
        autopay-allowed (subscribe [:payment.sources/can-enable-autopay?])
        card-sources    (subscribe [:payment/sources :card])
        service-source  (subscribe [:payment.sources/service-source])
        setting-svc-src (subscribe [:loading? :payment.source/set-default!])]
    [:div.page-controls.columns
     [:div.column {:style {:padding 0}}

      [:span.bold.mr1
       {:class (when-not @autopay-allowed "subdued")}
       (if @autopay-on "Autopay On" "Autopay Off")]

      [autopay-tooltip
       [ant/switch {:checked   @autopay-on
                    :disabled  (not @autopay-allowed)
                    :on-change #(dispatch [:payment.sources.autopay/confirm-modal @autopay-on])}]]

      [:span.ml1
       [tooltip/info
        (r/as-element
         [:span "With Autopay enabled, rent payments will automatically be withdrawn from your bank account on the "
          [:b "1st"] " of each month."])]]]

     [:div.column {:style {:padding 0}}
      [:span.bold.mr2 "Pay for Services with:"]
      (if @setting-svc-src
        [ant/spin {:style {:width 150}}]
        [ant/select
         {:style     {:width 150}
          :value     (:id @service-source)
          :disabled  (empty? @card-sources)
          :on-change #(dispatch [:payment.source/set-default! %])}
         (doall
          (for [{id :id :as source} @card-sources]
            ^{:key id} [ant/select-option {:value id :disabled (= id (:id @service-source))}
                        (payments-ui/source-name source)]))])
      [:span.ml1
       [tooltip/info
        (if (empty? @card-sources)
          "To pay for premium services, please link a credit or debit card."
          "This is the payment method that will be used for premium service orders.")]]]]))


(defn- source-view [sources]
  (if (empty? sources)
    ;; Empty State
    [no-sources]
    ;; Show Sources
    [:div
     [source-settings]
     [:div.columns
      [:div.column.fw200
       [:h3 "Accounts"]
       [source-list]]
      [:div.column
       [:h3 "Details"]
       [source-detail]
       [:h3 "Transaction History"]
       [source-payment-history]]]]))


(defn sources []
  (let [is-loading (subscribe [:loading? :payment.sources/fetch])
        sources    (subscribe [:payment/sources])]
    [:div
     [modal-add-source]
     [modal-confirm-remove-account]
     [modal-verify-account]
     [modal-confirm-enable-autopay]
     [modal-confirm-disable-autopay]
     (typography/view-header
      (l10n/translate :payment-sources)
      "Edit your payment accounts, enable Autopay, and set default payment sources.")
     (if (and (empty? @sources) @is-loading)
       [:div.loading-box.tall [ant/spin]]
       (source-view @sources))]))
