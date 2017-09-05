(ns odin.profile.payments.sources.views.forms
  (:require [antizer.reagent :as ant]
            [odin.components.validation :as validation]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]))


(defn is-form-valid?
  [errors _]
  (nil? errors))

(defn submit-card-if-valid
  [errors fields]
  (if (nil? errors)
    (tb/log "Form is valid! I will submit it." (js->clj fields))
    (tb/log "There are errors. Not submitting.")))


(def ^:private form-style
  {:label-col   {:span 7}
   :wrapper-col {:span 10}})


(defn credit-card []
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
                                         :on-change   #(dispatch [:payment.sources.add-new-account/update-card :full-name (-> % .-target .-value)])}])]

        [ant/form-item (merge form-style {:label "Card #"})
         (ant/decorate-field my-form "Card Number" {:rules [;;{:pattern validation/credit-card-number
                                                            ;;:message "Please enter a valid credit card number."}
                                                            {:required true}]}
                             [ant/input {:placeholder "1111-2222-3333-4444"
                                         :style       {:width "150px"}
                                         :on-change   #(dispatch [:payment.sources.add-new-account/update-card :card-number (-> % .-target .-value)])}])]

        [ant/form-item (merge form-style {:label "Exp. Date"})
         (ant/decorate-field my-form "Expiration date" {:rules [;;{:pattern validation/credit-card-exp-date
                                                                ;;:message "Please enter a valid expiration date, such as 01/21 or 01/2021."
                                                                {:required true}]}
                             [ant/input {:placeholder "09/2021"
                                         :style       {:width "90px"}
                                         :on-change   #(dispatch [:payment.sources.add-new-account/update-card :expiration (-> % .-target .-value)])}])]

        [ant/form-item (merge form-style {:label "CVV"})
         (ant/decorate-field my-form "CVV" {:rules [{:pattern validation/credit-card-cvv
                                                     :message "Your CVV is a 3-4 digit number located on the back of your card."}
                                                    {:required true}]}
                             [ant/input {:placeholder "123"
                                         :style       {:width "50px"}
                                         :on-change   #(dispatch [:payment.sources.add-new-account/update-card :cvv (-> % .-target .-value)])}])]]
       [:hr]
       [:div.align-right
        [:a.button {:on-click #(dispatch [:payment.sources.add/hide])} "Cancel"]
        [:a.button.is-primary {:on-click submit-if-valid} "Add Credit Card"]]])))


(defn bitcoin-account []
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
    [:a.button {:on-click #(dispatch [:payment.sources.add/hide])} "Cancel"]
    [:a.button.is-primary "OK"]]])


(defn- form-item-props [label]
  (merge form-style {:label label}))


(defn- form-item [{:keys [key label ant-id input-props rules]}]
  (let [form (ant/get-form)]
    [ant/form-item (form-item-props label)
     (ant/decorate-field form ant-id {:rules rules} [ant/input input-props])]))


(def ^:private bank-form-items
  [{:key         :account-holder
    :label       "Name of Account Holder"
    :ant-id      "Full name"
    :rules       [{:required true}]
    :input-props {:placeholder "Jane S. Doe"}}
   {:key         :routing-number
    :label       "Routing #"
    :ant-id      "Routing number"
    :rules       [{:required true}
                  {:len 9}]
    :input-props {:placeholder "110000000"}}
   {:key         :account-number
    :label       "Account #"
    :ant-id      "Account number"
    :rules       [{:pattern "^(\\d+)(\\d+|-)*$"
                   :message "Account number should begin with a digit, and contain only digits and hyphens."}
                  {:required true}
                  {:max 20}]
    :input-props {:placeholder "000123456789"}}])


(defn- bank-account-form []
  (let [form      (ant/get-form)
        on-change (fn [k] #(dispatch [:payment.sources.add-new-account/update-bank k (.. % -target -value)]))]
    [ant/form
     (map-indexed
      (fn [idx {key :key :as item}]
        (-> (assoc-in item [:input-props :on-change] (on-change key))
            (form-item)
            (with-meta {:key idx})))
     bank-form-items)]))


(def ^:private bank-account-desc
  "Upon adding your bank account, we'll make two small transactions to verify
  ownership. Your account will be ready to use after you've verified the amounts
  contained in those transactions. (Note: It may take up to 2 days for these
  transactions to appear.)")


(defn- submit-when-valid
  [form event]
  (let [submit* (fn [errors _] (when (nil? errors) (dispatch event)))]
    #(ant/validate-fields form submit*)))

(defn bank-account []
  (fn []
    (let [form (ant/get-form)]
      [:div
       (bank-account-form)
       [:p.pad bank-account-desc]
       [:hr]
       [:div.align-right
        [:a.button {:on-click #(dispatch [:modal/hide :payment.source/add])} "Cancel"]
        [:a.button {:on-click #(ant/reset-fields form)} "Clear Form"]
        [:a.button.is-primary
         {:on-click (submit-when-valid form [:payment.sources.add-new-account/submit-bank!])}
         "Add Bank Account"]]])))
