(ns odin.profile.settings.views
  (:require [odin.content :as content]
            [antizer.reagent :as ant]
            [odin.routes :as routes]))

(def form-style {:label-col   {:span 3}
                 :wrapper-col {:span 10}})


(defmethod content/view :profile/settings [route]
  [:div.column
   [:h1 "Settings"]
   [ant/card {:title "Contact Info"}
    [ant/form {:layout "horizontal"}
      [ant/form-item (merge form-style {:label "First Name"})
        [ant/input]]
      [ant/form-item (merge form-style {:label "Last Name"})
        [ant/input]]]]])
