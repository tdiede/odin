(ns odin.profile.settings.views
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]))


(def form-style {})
  ;;{:label-col   {:span 7}
   ;;:wrapper-col {:span 10})

(def password-placeholder "••••••••")


(defn change-password []
  [:div
   [:div.view-header
    [:h1 "Change Password"]]
   [:div.columns
    [:div.column.is-8
     [ant/card
      [ant/form {:layout "vertical"}
        [ant/form-item (merge form-style {:label "Existing Password"})
         [ant/input {:type "password"
                     :placeholder password-placeholder}]]
        ;;[:hr]
        [ant/form-item (merge form-style {:label "New Password"})
         [ant/input {:type "password"
                     :placeholder password-placeholder}]]

        [ant/form-item (merge form-style {:label "Confirm New Password"})
         [ant/input {:type "password"
                     :placeholder password-placeholder}]]

        [ant/form-item (merge form-style {:label " "})
         [:button.button {:class "is-primary"} "Update Password"]]]]]]])
