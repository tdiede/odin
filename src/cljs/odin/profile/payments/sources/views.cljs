(ns odin.profile.payments.sources.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [odin.routes :as routes]
            [odin.l10n :as l10n]
            [toolbelt.core :as tb]
            [odin.routes :as routes]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [odin.utils.formatters :as format]
            [odin.components.ui :as ui]
            [odin.components.payments :as payments-ui]
            [odin.components.validation :as validator]
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
      [:a.panel-block {:class (when (= id (get current-source :id)) "is-active")
                       :href  (routes/path-for :profile.payment/sources :query-params {:source-id id})}
                       ;;:on-click #(dispatch [:payment.sources/set-current source])}
       [:span.panel-icon
        (payments-ui/payment-source-icon (or type :bank))]
       [:span.flexrow.full-width
        [:span name]
        [:span.flex-pin-right (str "**** " last4)]]]))])



(defn source-detail
  "Display information about the currently-selected payment source."
  [source]
  (let [{type :type
         name :name
         last4 :last4
         autopay-on :autopay} source]
   [:div.card
    [:div.card-content
     [:div.flexrow
      ;; Source Name
      [payments-ui/payment-source-icon type]
      [:h3 (str name " **** " last4)]]]
    ;; Buttons
    [:footer.card-footer
     [:div.card-footer-item]
     (if (= autopay-on true)
      [:a.card-footer-item {:class "is-success"}
        [:span.icon.is-small [:i.fa.fa-check-circle]]
        [:span "Autopay On"]]
      [:a.card-footer-item
        [:span "Enable Autopay"]])
     [:a.card-footer-item.is-danger "Unlink"]]]))


(defn source-payment-history
  "Display the transaction history for a given payment source."
  [source]
  (let [{txs  :payments
         name :name} source]
    [ant/card {:title (l10n/translate :payment-history-for name)
               :class "is-flush"}
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


(def ^:private form-style
  {:label-col   {:span 7}
   :wrapper-col {:span 10}})


(defn form-add-new-bank-account [is-visible?]
  (fn [props]
   (let [my-form (ant/get-form)]
    [:div
     [ant/form
       [ant/form-item (merge form-style {:label "Name of Account Holder"})
        (ant/decorate-field my-form "Full name" {:rules [{:required true}]}
         [ant/input {:placeholder "Jane S. Doe"
                     :on-change #(dispatch [:payment.sources.add-new-account/update-bank :full-name (-> % .-target .-value)])}])]
       [ant/form-item (merge form-style {:label "Account #"})
        (ant/decorate-field my-form "Account number" {:rules [{:pattern "^(\\d+)(\\d+|-)*$"
                                                               :message "Account number should begin with a digit, and contain only digits and hyphens."}
                                                              {:required true}
                                                              {:max 20}]}
         [ant/input {:placeholder "1110000000"
                     :on-change #(dispatch [:payment.sources.add-new-account/update-bank :account-number (-> % .-target .-value)])}])]
       [ant/form-item (merge form-style {:label "Routing #"})
        (ant/decorate-field my-form "Routing number" {:rules [{:required true
                                                               :len 9}]}
         [ant/input {:placeholder "1001234567"
                     :on-change #(dispatch [:payment.sources.add-new-account/update-bank :routing-number (-> % .-target .-value)])}])]]
     [:p.pad "Upon adding your bank account, we'll make two small transactions to verify ownership.
          Your account will be ready to use after you've verified the amounts contained in those transactions.
          (Note: It may take up to 2 days for these transactions to appear.)"]
     [:hr]
     [:div.align-right
      [:a.button {:on-click #(reset! is-visible? false)} "Cancel"]
      [:a.button {:on-click #(ant/reset-fields my-form)} "Clear Form"]
      [:a.button.is-primary {:on-click #(dispatch [:payment.sources.add-new-account/submit-bank!])}
       "Add Bank Account"]]])))

(defn is-form-valid?
  [errors _]
  (nil? errors))

(defn submit-card-if-valid
  [errors fields]
  (if (nil? errors)
    (tb/log "Form is valid! I will submit it." (js->clj fields))
    (tb/log "There are errors. Not submitting.")))

(defn form-add-new-credit-card [is-visible?]
  (fn [props]
   (let [my-form         (ant/get-form)
         submit-if-valid #(ant/validate-fields my-form submit-card-if-valid)]
    [:div
     [ant/form {:on-submit #(do
                              (.preventDefault %)
                              (submit-if-valid))}
                              ;;(ant/validate-fields my-form submit-card-if-valid))}
       [ant/form-item (merge form-style {:label "Full Name"})
        (ant/decorate-field my-form "Full Name" ;;{:rules [{:required true}]}
         [ant/input {:placeholder "Jane S. Doe"
                     :on-change #(dispatch [:payment.sources.add-new-account/update-card :full-name (-> % .-target .-value)])}])]

       [ant/form-item (merge form-style {:label "Card #"})
        (ant/decorate-field my-form "Card Number" {:rules [;;{:pattern validator/credit-card-number
                                                            ;;:message "Please enter a valid credit card number."}
                                                           {:required true}]}
         [ant/input {:placeholder "1111-2222-3333-4444"
                     :style {:width "150px"}
                     :on-change #(dispatch [:payment.sources.add-new-account/update-card :card-number (-> % .-target .-value)])}])]

       [ant/form-item (merge form-style {:label "Exp. Date"})
        (ant/decorate-field my-form "Expiration date" {:rules [;;{:pattern validator/credit-card-exp-date
                                                                ;;:message "Please enter a valid expiration date, such as 01/21 or 01/2021."
                                                               {:required true}]}
         [ant/input {:placeholder "09/2021"
                     :style {:width "90px"}
                     :on-change #(dispatch [:payment.sources.add-new-account/update-card :expiration (-> % .-target .-value)])}])]

       [ant/form-item (merge form-style {:label "CVV"})
        (ant/decorate-field my-form "CVV" {:rules [{:pattern validator/credit-card-cvv
                                                    :message "Your CVV is a 3-4 digit number located on the back of your card."}
                                                   {:required true}]}
         [ant/input {:placeholder "123"
                     :style {:width "50px"}
                     :on-change #(dispatch [:payment.sources.add-new-account/update-card :cvv (-> % .-target .-value)])}])]]
     [:hr]
     [:div.align-right
      [:a.button {:on-click #(reset! is-visible? false)} "Cancel"]
      [:a.button.is-primary {:on-click #(submit-if-valid)} "Add Credit Card"]]])))


(defn form-add-bitcoin-account [is-visible?]
  [:div
   ;;[:p "Deposit address: 12398asdj123123az"]
   [:div.card
    [:div.card-content.align-center
     [:div.width-90.center
      [:h3 "Deposit Address"]
      [:pre.is-size-4 "1F1tAaz5x1HUXrCNLbtMDqcw6o5GNn4xqX"]
      [:br]
      [:p.is-size-6. "BTC sent to this address will credit toward your Starcity account balance, which you can then use to make payments."]]]]
   [:hr]
   [:div.align-right
    [:a.button {:on-click #(reset! is-visible? false)} "Cancel"]
    [:a.button.is-primary "OK"]]])

(defn modal-add-account
  [is-visible?]
  (let [account-type (subscribe [:payment-sources/new-account-type])]
   [ant/modal {:title     (l10n/translate :btn-add-new-account)
               :width     "640px"
               :visible   @is-visible?
               :on-ok     #(reset! is-visible? false)
               :on-cancel #(reset! is-visible? false)
               :footer    nil}
    [:div
     [:div.tabs.is-centered
      [:ul
       [:li {:class (when (= @account-type :bank) "is-active")}
        [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :bank])}
         [:span.icon.is-small [:i.fa.fa-bank]]
         [:span "Bank Account"]]]
       [:li {:class (when (= @account-type :card) "is-active")}
        [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :card])}
         [:span.icon.is-small [:i.fa.fa-credit-card]]
         [:span "Credit Card"]]]
       [:li {:class (when (= @account-type :bitcoin) "is-active")}
        [:a {:on-click #(dispatch [:payment.sources.add-new-account/select-type :bitcoin])}
         [:span.icon.is-small [:i.fa.fa-bitcoin]]
         [:span "Bitcoin"]]]]]
     (case @account-type
       :bank    (r/as-element (ant/create-form (form-add-new-bank-account is-visible?)))
       :card    (r/as-element (ant/create-form (form-add-new-credit-card is-visible?)))
       :bitcoin [form-add-bitcoin-account is-visible?])]]))
     ;;(when (= @account-type :bank) (r/as-element (ant/create-form (form--add-new-bank-account is-visible?))))
     ;;(when (= @account-type :card) [form--add-new-credit-card is-visible?])
     ;;(when (= @account-type :bitcoin) [form--add-bitcoin-account is-visible?])]]))
;;(ant/decorate-field my-form "password" {:rules [{:required true}]})


(defn add-new-source-button
  "Button for adding a new Payment Source."
  [modal-shown?]
  [:nav.panel.space-top.is-rounded
   [:a.panel-block {:on-click #(swap! modal-shown? not)}
    [:span.panel-icon
     [:span.icon [:i.fa.fa-plus-square-o]]]
    (l10n/translate :btn-add-new-account)
    [modal-add-account modal-shown?]]])


(defn no-sources [modal-shown?]
  [:div
   [:div.box
    [:h3 "You don't have any accounts linked yet."]

    [:div.steps-vertical
     [ui/media-step "Link a payment source so you can settle your charges." "bank"]
     [ui/media-step "Turn on Autopay and never worry about a late payment again." "history"]
     [ui/media-step
      [:a.button.is-primary {:on-click #(swap! modal-shown? not)}
       [:span.icon.is-small [:i.fa.fa-plus-square-o]]
       [:span (l10n/translate :btn-add-new-account)]]]]]])


(defn sources []
  (let [sources              (subscribe [:payment-sources])
        current-source       (subscribe [:payment-sources/current])
        loading              (subscribe [:payment-sources.list/loading?])
        modal-add-source?    (r/atom false)
        modal-remove-source? (r/atom false)
        modal-disable-autop? (r/atom false)]
    [:div
     [:div.view-header
      [:h1 (l10n/translate :payment-sources)]
      [:p "View and manage your payment sources."]]
     (if (empty? @sources)
       ;; Empty State
       [no-sources modal-add-source?]

       ;; Show Sources
       [:div.columns
        [:div.column.is-4
         [source-list @sources @current-source]
         [add-new-source-button modal-add-source?]]

        [:div.column.is-8
         [source-detail @current-source]
         [source-payment-history @current-source]]])
         ;;[ant/card
         ;; "Unverified"
         ;; [:a.button.is-primary "Verify Now"]]]]

     [modal-add-account modal-add-source?]]))
     ;;[modal-confirm-disable-autopay modal-disable-autop?]]))
