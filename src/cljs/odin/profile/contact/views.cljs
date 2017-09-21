(ns odin.profile.contact.views
  (:require [antizer.reagent :as ant]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]))

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


(defn- form-item [{:keys [key label ant-id input-props rules initial-value]}]
  (let [form (ant/get-form)]
    [ant/form-item (form-item-props label)
      (ant/decorate-field form ant-id {:rules rules
                                       :initial-value initial-value} [ant/input input-props])]))


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
    :label       "Email Address"
    :ant-id      "email"
    :rules       [{:required false}]
    :input-props {:placeholder (placeholder :email)
                  :disabled    true}}])


(defn- contact-info-form []
  (let [form      (ant/get-form)
        account   @(subscribe [:profile/account-mutable])
        on-change (fn [k] #(dispatch [:profile.contact.info/update! k (.. % -target -value)]))]
    [ant/form {:layout "horizontal"}
      (map-indexed
       (fn [idx {key :key :as item}]
         (-> (assoc-in item [:input-props :on-change] (on-change key))
             (assoc-in [:initial-value] (get account key))
             (form-item)
             (with-meta {:key idx})))
       contact-info-form-items)]))


(def ^:private emergency-contact-form-items
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
    :input-props {:placeholder (placeholder :phone)}}])


(defn- emergency-contact-info-form []
  (let [form      (ant/get-form)
        account   (subscribe [:profile/account-mutable])
        contact   (get @account :emergency_contact)
        on-change (fn [k] #(dispatch [:profile.contact.info/update-emergency-contact! k (.. % -target -value)]))]

    [ant/form {:layout "horizontal"}
      (map-indexed
       (fn [idx {key :key :as item}]
         (-> (assoc-in item [:input-props :on-change] (on-change key))
             (assoc-in [:initial-value] (get contact key))
             (form-item)
             (with-meta {:key idx})))
       emergency-contact-form-items)]))


(defn- contact-info-ui []
  (fn []
    (let [form (ant/get-form)]
      [:div
       (contact-info-form)
       [ant/form-item (merge form-style {:label " "})
        [ant/button {:disabled true} "Save Changes"]]])))


(defn- emergency-contact-ui []
  (fn []
    (let [form (ant/get-form)]
      [:div
       (emergency-contact-info-form)
       [ant/form-item (merge form-style {:label " "})
        [ant/button {:disabled true} "Save Changes"]]])))


(defn contact-info []
  [:div
   [:div.view-header
    [:h1.title.is-3 "Contact Information"]
    [:p.subtitle.is-6 "Update your info in our system, including an emergency contact."]]

   [:div.columns
    [:div.column.is-8

     [ant/card {:title "Personal Info"}
      (r/as-element (ant/create-form (contact-info-ui)))]

     [ant/card {:title "Emergency Contact"}
      (r/as-element (ant/create-form (emergency-contact-ui)))]]]])
