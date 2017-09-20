(ns odin.profile.payments.sources.views
  (:require [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.profile.payments.sources.autopay :as autopay]
            [odin.components.ui :as ui]
            [odin.components.input :as input]
            [odin.profile.payments.sources.views.forms :as forms]
            [odin.l10n :as l10n]
            [odin.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))


(defn source-list-item
  [{:keys [id type name last4] :as source}]
  (let [current (subscribe [:payment.sources/current])]
    [:a.source-list-item
     {:class (when (= id (get @current :id)) "is-active")
      :href  (routes/path-for :profile.payment/sources
                              :query-params {:source-id id})}

     [:p.source-card-name name]
     [:p.source-card-digits
      ;;[:span.mr1 (payments-ui/payment-source-icon (or type :bank) "is-small")]
      [:span (case type
               :card (str "xxxxxxxxxxxx" last4)
               (str "xxxxxx" last4))]]

     ;;[:span.payment-icon-top-right
     ;;(payments-ui/payment-source-icon (or type :bank))]
     [:div.source-list-metadata.flexrow
      [:span.source-list-type-icon.flexrow
       (payments-ui/payment-source-icon (or type :bank))
       [:span.ml1 (:expires source)]]
      (when (true? (:default source))
        [ant/tooltip {:title "Default payment source"}
         [:div.default-source-indicator
          [:span.icon.icon-default-source [:i.fa.fa-check-circle]]
          [:span.default-source-label "Default"]]])]]))



(defn source-list
  "A vertical menu listing the linked payment sources."
  []
  (let [sources (subscribe [:payment/sources])]
    ;; TODO: Show a loading state?
    ;; loading (subscribe [:payment.sources/loading?])
    [:div
     [:h3 "Linked Accounts"]
     [:div.source-list.mb6
      ;;[:nav.panel.is-rounded
      ;;[:p.panel-heading "Linked Accounts"]
      (doall
       (map-indexed
        #(with-meta [source-list-item %2] {:key %1})
        @sources))

      [:a.source-list-item.add-new-source
       {:on-click #(dispatch [:modal/show :payment.source/add])}
       [ant/icon {:type "plus-circle-o" :style {:font-size "2rem"}}]
       [:p.title.is-6 "Add Payment Method"]]]]))


(defn- source-actions-menu []
  [ant/menu
   [ant/menu-item {:key "removeit"}
    [:a {:href "#" :on-click #(dispatch [:modal/show :payment.source/remove])}
     "Remove this account"]]])


(defn source-detail
  "Display information about the currently-selected payment source."
  []
  (let [{:keys [id type name last4 autopay-on] :as source} @(subscribe [:payment.sources/current])
        is-unverified (and (= type :bank) (not= (:status source) "verified"))
        is-default     (and (= type :bank) (true? (:default source)))
        can-be-default (and (= type :bank) (not (true? is-default)))
        can-be-autopay (= type :bank)]
    [ant/card ;;{:title "Details" :class "stripe-style"}
     [:div.flexrow
      ;; Source Icon
      ;;[payments-ui/payment-source-icon type]
      [:div
       ;; Source Name
       [:h3 (str name " **** " last4)]
       ;;[payments-ui/payment-source-icon type]
       ;; Is default source?
       [:div.flexrow.mt1
        (when is-default
          [ant/tag {:color "blue" :class "is-medium noop"}
           [ant/icon {:type "check-circle"}]
           [:span.text-blue "Default method"]])
        (when can-be-autopay
          [ant/tag {:class "noop is-medium"}
           [ant/icon {:type "close"}]
           [:span "Autopay Off"]])]]

      [:div.pin-right.pin-top
       ;;(when can-be-default
       ;;[ant/button {:on-click #(dispatch [:payment.source/set-default! id])} "Set as default"]
       (when is-unverified
         [ant/button {:on-click #(dispatch [:modal/show :payment.source/verify-account])} "Verify Account"])
       ;;[ant/button {:on-click #(dispatch [:payment.sources.autopay/enable! (:id source)])}
       ;;     "Enable for Autopay"]
       [ant/dropdown {:trigger ["click"]
                      :overlay (r/as-element [source-actions-menu])}
        [:a.ant-dropdown-link
         [:span "More"]
         [ant/icon {:type "down"}]]]]]]))

;; Buttons
;;[:footer.card-footer
;; [:div.card-footer-item]
;; (if autopay-on
;;   [:a.card-footer-item {:class "is-success"}
;;    [:span.icon.is-small [:i.fa.fa-check-circle]]
;;    [:span "Autopay On"]]
;;   [:a.card-footer-item [:span "Enable Autopay"]])
;; [:a.card-footer-item.is-danger "Unlink"]]]))


(defn source-payment-history
  "Display the transaction history for a given payment source."
  []
  (let [{:keys [payments name]} @(subscribe [:payment.sources/current])
        is-loading              (subscribe [:loading? :payment.sources/fetch])]
    [ant/card {:title "Transaction History" ;;(l10n/translate :payment-history-for name)
               :class "is-flush stripe-style"}
     [payments-ui/payments-table payments @is-loading]]))


(defn modal-confirm-enable-autopay []
  (let [is-visible (subscribe [:modal/visible? :payment.source/autopay-enable])
        banks      (subscribe [:payment/sources :bank])
        selected   (r/atom (-> @banks first :id))]
    [ant/modal {:title       "Autopay your rent?"
                :visible     @is-visible
                :ok-text     "Great! Let's do it"
                :cancel-text "I'd rather pay manually."
                :on-ok       #(dispatch [:payment.sources.autopay/enable! @selected])
                :on-cancel   #(dispatch [:modal/hide :payment.source/autopay-enable])}
     [:div
      [:p "Autopay automatically transfers your rent the day before it's due,
            on the 1st of each month."]
      [:p "We recommend enabling this
            feature, so you never need to worry about making rent on time."]
      ]]))


(defn modal-confirm-disable-autopay []
  (let [is-visible (subscribe [:modal/visible? :payment.source/autopay-disable])]
    ;;(tb/log (str "is disable modal visible?" @is-visible))
    [ant/modal {:title     (l10n/translate :confirm-unlink-autopay)
                :visible   @is-visible
                :on-ok     #(dispatch [:payment.sources.autopay/disable!])
                :on-cancel #(dispatch [:modal/hide :payment.source/autopay-disable])}
     ;;:footer    nil}
     [:div
      [:p "Autopay automatically transfers your rent each month, one day before your Due Date. We recommend enabling this feature, so you never need to worry about making rent on time."]]]))


(defn modal-verify-account []
  (let [is-visible (subscribe [:modal/visible? :payment.source/verify-account])
        ;; is-submitting (subscribe [:loading? :payment.sources.bank/verify])
        current-id (subscribe [:payment.sources/current-id])
        amounts    (r/atom {:amount-1 nil :amount-2 nil})]
    [ant/modal {:title     "Verify Bank Account"
                :visible   @is-visible
                :on-ok     #(dispatch [:payment.sources.bank/verify! @current-id 32 45])
                :on-cancel #(dispatch [:modal/hide :payment.source/verify-account])}
     [:div
      [:p "If the two microdeposits have posted to your account, enter them below to verify ownership."]
      [:p "Note: Amounts should be entered in " [:i "cents"] " (e.g. '32' not '0.32')"]
      [:form {:on-submit #(do
                            (.preventDefault %)
                            ;; TODO: Hook up form to supply `32` and `45`
                            )}
       [ant/input-number {:default-value (:amount-1 @amounts)
                          :placeholder   "e.g. 32"
                          :on-change     #(swap! amounts assoc :amount-1 %)}]
       [ant/input-number {:default-value (:amount-2 @amounts)
                          :placeholder   "e.g. 45"
                          :on-change     #(swap! amounts assoc :amount-2 %)}]]]]))


(defn modal-confirm-remove-account []
  (let [is-visible     (subscribe [:modal/visible? :payment.source/remove])
        current-source (subscribe [:payment.sources/current])]
    (fn []
      [ant/modal {:title     "Remove this account?"
                  :width     640
                  :visible   @is-visible
                  :ok-text   "Yes, remove"
                  :on-ok     #(dispatch [:payment.source/delete! (:id @current-source)])
                  :on-cancel #(dispatch [:modal/hide :payment.source/remove])}
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


;; NOTE: This is a simple example of how to componentize a common UI component.
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


(defn add-new-source-button
  "Button for adding a new Payment Source."
  []
  [ant/button {:type "primary"
               :size "large"
               :icon "plus-circle-o"
               :on-click #(dispatch [:modal/show :payment.source/add])}
   (l10n/translate :btn-add-new-account)])


(defn no-sources []
  [:div.box
   [:h3 "You don't have any accounts linked yet."]

   [:div.steps-vertical
    [ui/media-step "Link a payment source so you can settle your charges." "bank"]
    [ui/media-step "Turn on Autopay and never worry about a late payment again." "history"]
    [ui/media-step
     [ant/button {:type "primary"
                  :on-click #(dispatch [:modal/show :payment.source/add])}
      [:span.icon.is-small [:i.fa.fa-plus-square-o]]
      [:span (l10n/translate :btn-add-new-account)]]]]])



(defn source-settings []
  (let [autopay-on  (subscribe [:payment.sources/autopay-on?])
        src-default (subscribe [:payment.sources/default-source])]
    [:div.page-controls.flexrow.rounded.space-around.bg-gray.mb6
     [:div.flexrow.flex-center
      [ant/switch {:checked   @autopay-on
                   :on-change #(dispatch [:payment.sources.autopay/confirm-modal])}]
      [:p.ml1
       [:span.bold "Autopay"]
       [ui/info-tooltip "When you enable Autopay, rent payments will automatically be applied on the 1st of each month during your rental period."]]]
     [:div.ml4
      [:p
       "Rent payments will post to your "
       [:span.bold {:style {:margin "0 0.15rem"}}
        [ant/icon {:type "check-circle"}]
        [:span "Default Source"]]
       "on the 1st of each month."]]]))


(defn- source-view []
  (let [sources (subscribe [:payment/sources])]
    (if (empty? @sources)
      ;; Empty State
      [no-sources]
      ;; Show Sources
      [:div
       [source-settings]
       [source-list]
       [:div.columns
        [:div.column
         [source-detail]
         [source-payment-history]]]])))


(defn sources []
  (let [loading (subscribe [:loading? :payment.sources/fetch])]
    [:div
     [modal-add-source]
     [modal-confirm-remove-account]
     [modal-verify-account]
     [modal-confirm-enable-autopay]
     [modal-confirm-disable-autopay]
     [:div.view-header.flexrow
      [:div
       [:h1 (l10n/translate :payment-sources)]
       [:p "Edit your payment accounts, enable Autopay, and set default payment sources."]]]
     ;;[:div.pin-right
     ;;[add-new-source-button]]]
     (if (= @loading true)
       [:div.loading-box.tall [ant/spin]]
       (source-view))]))
