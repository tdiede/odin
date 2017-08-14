(ns odin.components.widgets
  (:require [odin.l10n :refer [translate]]
            [antizer.reagent :as ant]))

(defn stripe-icon-link
  "Renders a Stripe icon that links to a transaction on Stripe."
  [uri]
  [ant/tooltip {:title     (translate :view-on-stripe)
                :placement "right"}
   [:a {:href uri}
    [:span.icon.is-small
     [:i.fa.fa-cc-stripe]]]])
