(ns onboarding.prompts.services.cleaning
  (:require [onboarding.components.catalogue :as catalogue]
            [onboarding.prompts.content :as content]))

(defmethod content/content :services/cleaning
  [{:keys [keypath data] :as item}]
  (let [{:keys [orders catalogue]} data]
    [:div.content
     [:p "Eliminate tedium from your life with our cleaning and laundry services."]

     [catalogue/grid keypath catalogue orders :grid-size 1]]))
