(ns odin.profile.contact.views
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [odin.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]
            [toolbelt.core :as tb]))

(defn- placeholder
  [field]
  (case field
    :first_name "Daffy"
    :last_name  "Duck"
    :phone      "405-555-1234"
    :email      "me@joinstarcity.com"
    :bio        "Give us a short introduction to yourself! This will help your neighbors in the community to connect with you about mutual interests, etc."))



(def ^:private form-style
  {:label-col   {:span 6}
   :wrapper-col {:span 10}})


(defn- form-item-props [label]
  (merge form-style {:label label}))


(defn- form-item [{:keys [key label ant-id input-props rules]}]
  (let [form (ant/get-form)]
    [ant/form-item (form-item-props label)
     (ant/decorate-field form ant-id {:rules rules} [ant/input input-props])]))


(def ^:private contact-info-form-items
  [{:key         :first_name
    :label       "First Name"
    :ant-id      "first-name"
    :rules       [{:required true}]
    :input-props {:placeholder (placeholder :first_name)}}
   {:key         :last_name
    :label       "Last Name"
    :ant-id      "last-name"
    :rules       [{:required true}]
    :input-props {:placeholder (placeholder :last_name)}}
   {:key         :phone
    :label       "Phone #"
    :ant-id      "phone"
    :rules       [{:required true}]
    :input-props {:placeholder (placeholder :phone)}}
   {:key         :email
    :label       "Email Adress"
    :ant-id      "email"
    :input-props {:placeholder (placeholder :email)
                  :disabled    true}}
   {:key         :bio
    :label       "Short Bio"
    :ant-id      "bio"
    :rules       [{:required true}]
    :input-props {:placeholder (placeholder :bio)
                  :type        "textarea"
                  :style       {:min-height "100px"}}}])



(defn- contact-info-form []
  (let [form      (ant/get-form)
        on-change (fn [k] #(dispatch [:profile.contact.info/update! k (.. % -target -value)]))]
    [ant/form
      (map-indexed
       (fn [idx {key :key :as item}]
         (-> (assoc-in item [:input-props :on-change] (on-change key))
             (form-item)
             (with-meta {:key idx})))
       contact-info-form-items)]))


;;(defn- contact-info-form []
;;  (let [form      (ant/get-form)
;;        account   (subscribe [:profile/account-mutable])]
;;        ;;on-change (fn [k] #(dispatch [:payment.sources.add.bank/update! k (.. % -target -value)]))]
;;
;;    [ant/form {:layout "horizontal"}
;;
;;      [:fieldset
;;       ;;[:h4.fieldset-label [:span "Personal Information"]]
;;       [ant/form-item (merge form-style {:label "First Name"})
;;         [ant/input {:type        "text"
;;                     :value       (get @account :first_name)
;;                     :placeholder (placeholder :first_name)
;;                     :on-change   #(dispatch [:profile.contact.info/update! :first_name (.. % -target -value)])}]]
;;
;;       [ant/form-item (merge form-style {:label "Last Name"})
;;         [ant/input {:type        "text"
;;                     :value       (get @account :last_name)
;;                     :placeholder (placeholder :last_name)}]]
;;
;;       [ant/form-item (merge form-style {:label "Email Address"})
;;         [ant/input {:type     "text"
;;                     :disabled true
;;                     :value    (get @account :email)}]]
;;
;;
;;       [ant/form-item (merge form-style {:label "Phone Number"})
;;         [ant/input {:type        "text"
;;                     :value       (get @account :phone)
;;                     :placeholder (placeholder :phone)}]]
;;
;;       [ant/form-item (merge form-style {:label "Short Bio"
;;                                         :wrapper-col {:span 14}})
;;         [ant/input {:type        "textarea"
;;                     :style       {:min-height "100px"}
;;                     :value       (get @account :bio)
;;                     :placeholder (placeholder :bio)}]]]
;;
;;      ;;[:hr]
;;      [ant/form-item (merge form-style {:label " "})
;;       [:button.button.is-primary {:disabled true} "Save Changes"]]]))


(defn- emergency-contact-info-form []
  (let [form      (ant/get-form)
        account   (subscribe [:profile/account-mutable])
        emergency_contact (get @account :emergency_contact)]
        ;;on-change (fn [k] #(dispatch [:payment.sources.add.bank/update! k (.. % -target -value)]))]

    [ant/form {:layout "horizontal"}
      [:fieldset
       ;;[:h4.fieldset-label [:span "Emergency Contact"]]

       [ant/form-item (merge form-style {:label "First Name"})
         [ant/input {:type        "text"
                     :value       (get emergency_contact :first_name)
                     :placeholder (placeholder :first_name)}]]

       [ant/form-item (merge form-style {:label "Last Name"})
         [ant/input {:type        "text"
                     :value       (get emergency_contact :last_name)
                     :placeholder (placeholder :last_name)}]]

       [ant/form-item (merge form-style {:label "Phone Number"})
         [ant/input {:type        "text"
                     :value       (get emergency_contact :phone)
                     :placeholder (placeholder :phone)}]]]


      ;;[:hr]
      [ant/form-item (merge form-style {:label " "})
       [:button.button.is-primary {:disabled true} "Save Changes"]]]))


(defn- contact-info-ui []
  (fn []
    (let [form (ant/get-form)]
      [:div
       (contact-info-form)
       [ant/form-item (merge form-style {:label " "})
        [:button.button.is-primary {:disabled true} "Save Changes"]]])))


(defn contact-info []
  [:div
   [:div.view-header
    [:h1 "Contact Information"]
    [:p "Update your info in our system, including an emergency contact."]]

   [:div.columns
    [:div.column.is-8

     [ant/card {:title "Personal Info"}
      (r/as-element (ant/create-form (contact-info-ui)))]]]])

     ;;[ant/card {:title "Emergency Contact"} (emergency-contact-info-form)]]]])
