(ns odin.kami.views
  (:require [odin.content :as content]
            [antizer.reagent :as ant]))


(defmethod content/view :kami [route]
  [:div
   [:div.view-header
    [:h1.title.is-3 "Kami"]
    [:p.subtitle.is-5 "Determine a building's potential as a Starcity."]]])
