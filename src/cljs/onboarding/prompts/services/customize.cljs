(ns onboarding.prompts.services.customize
  (:require [onboarding.components.catalogue :as catalogue]
            [onboarding.prompts.content :as content]))

(def ^:private description
  "If you'd like to make a few changes to help you feel more at home, let us
  know what you'd like to do and we'll do our best to accommodate.")

(defmethod content/content :services/customize
  [{:keys [keypath data] :as item}]
  (let [{:keys [orders catalogue]} data]
    [:div.content
     [:p {:dangerouslySetInnerHTML {:__html description}}]
     [catalogue/grid keypath catalogue orders :grid-size 1]]))
