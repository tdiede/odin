(ns onboarding.prompts.admin.emergency
  (:require [antizer.reagent :as ant]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]))


(defn- on-change [keypath k]
  #(dispatch [:prompt/update keypath k (.. % -target -value)]))


(defmethod content/content :admin/emergency
  [{:keys [keypath data] :as item}]
  (let [{:keys [first-name last-name phone-number]} data]
    [:div.content
     [:p "Please provide us with the contact information for a family member or close friend that we can get in touch with should we ever need to."]
     [:p "We promise to only call this person in an emergency."]
     [ant/card
      [:div.field.is-grouped
       [:div.control.is-expanded
        [:label.label "First Name"]
        [ant/input {:type      :text
                    :value     first-name
                    :required  true
                    :on-change (on-change keypath :first-name)}]]
       [:div.control.is-expanded
        [:label.label "Last Name"]
        [ant/input {:type      :text
                    :value     last-name
                    :required  true
                    :on-change (on-change keypath :last-name)}]]
       [:div.control.is-expanded
        [:label.label "Phone Number"]
        [ant/input {:type        :text
                    :required    true
                    :value       phone-number
                    :placeholder "e.g. 234-567-8910"
                    :on-change   (on-change keypath :phone-number)}]]]]]))
