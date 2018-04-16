(ns onboarding.prompts.services.upgrades
  (:require [onboarding.components.catalogue :as catalogue]
            [onboarding.prompts.content :as content]))

(def ^:private description
  ["Your suite comes fully furnished, but we provide additional items to tailor the room to suit your needs."
   "<i>There is a one-time delivery and installation fee for furniture rentals, which will be calculated based on the amount of furniture pieces ordered.</i>"])

(defmethod content/content :services/upgrades
  [{:keys [keypath data] :as item}]
  (let [{:keys [orders catalogue]} data]
    [:div.content
     (map-indexed #(with-meta [:p {:dangerouslySetInnerHTML {:__html %2}}] {:key %1}) description)
     [catalogue/grid keypath catalogue orders :grid-size 2]]))
