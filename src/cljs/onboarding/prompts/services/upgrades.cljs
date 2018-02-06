(ns onboarding.prompts.services.upgrades
  (:require [onboarding.components.catalogue :as catalogue]
            [onboarding.prompts.content :as content]))

(def ^:private description
  "Your suite comes fully furnished, but we provide additional items to tailor the room to suit your needs. All prices include installation, and rental prices are a one time fee for the entirety of your membership term.")

(defmethod content/content :services/upgrades
  [{:keys [keypath data] :as item}]
  (let [{:keys [orders catalogue]} data]
    [:div.content
     [:p {:dangerouslySetInnerHTML {:__html description}}]
     [catalogue/grid keypath catalogue orders :grid-size 2]]))
