(ns odin.profile.settings.views
  (:require [antizer.reagent :as ant]))


(def form-style
  {:label-col   {:span 3}
   :wrapper-col {:span 10}})


(defn change-password []
  [:div
   [:h1 "Change Password"]
   [ant/card
    [ant/form {:layout "horizontal"}
     [ant/form-item (merge form-style {:label "First Name"})
      [ant/input]]
     [ant/form-item (merge form-style {:label "Last Name"})
      [ant/input]]]]])
