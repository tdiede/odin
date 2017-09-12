(ns odin.profile.contact.views
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [odin.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]))

(defn- placeholder
  [field]
  (case field
    :first_name "Daffy"
    :last_name  "Duck"
    :phone      "405-555-1234"
    :bio        "Give us a short introduction to yourself! This will help your neighbors in the community to connect with you about mutual interests, etc."))



(def form-style
  {:label-col   {:span 6}
   :wrapper-col {:span 10}})


(defn- contact-info-form []
  (let [form      (ant/get-form)
        account   (subscribe [:profile/account])]
        ;;on-change (fn [k] #(dispatch [:payment.sources.add.bank/update! k (.. % -target -value)]))]

    [ant/form {:layout "horizontal"}

      [:fieldset
       ;;[:h4.fieldset-label [:span "Personal Information"]]
       [ant/form-item (merge form-style {:label "First Name"})
         [ant/input {:type        "text"
                     :value       (get @account :first_name)
                     :placeholder (placeholder :first_name)}]]

       [ant/form-item (merge form-style {:label "Last Name"})
         [ant/input {:type        "text"
                     :value       (get @account :last_name)
                     :placeholder (placeholder :last_name)}]]

       [ant/form-item (merge form-style {:label "Email Address"})
         [ant/input {:type     "text"
                     :disabled true
                     :value    (get @account :email)}]]


       [ant/form-item (merge form-style {:label "Phone Number"})
         [ant/input {:type        "text"
                     :value       (get @account :phone)
                     :placeholder (placeholder :phone)}]]

       [ant/form-item (merge form-style {:label "Short Bio"
                                         :wrapper-col {:span 14}})
         [ant/input {:type        "textarea"
                     :style       {:min-height "100px"}
                     :value       (get @account :bio)
                     :placeholder (placeholder :bio)}]]]

      ;;[:hr]
      [ant/form-item (merge form-style {:label " "})
       [:button.button.is-primary {:disabled true} "Save Changes"]]]))


(defn- emergency-contact-info-form []
  (let [form      (ant/get-form)
        account   (subscribe [:profile/account])
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



(defn contact-info []
  [:div
   [:div.view-header
    [:h1 "Contact Information"]
    [:p "Update your info in our system, including an emergency contact."]]

   [:div.columns
    [:div.column.is-8
     [ant/card {:title "Personal Info"} (contact-info-form)]
     [ant/card {:title "Emergency Contact"} (emergency-contact-info-form)]]]])
