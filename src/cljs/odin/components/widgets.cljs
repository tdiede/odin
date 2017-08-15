(ns odin.components.widgets
  (:require [odin.l10n :as l10n]
            [antizer.reagent :as ant]))

(defn stripe-icon-link
  "Renders a Stripe icon that links to a transaction on Stripe."
  [uri]
  [ant/tooltip {:title     (l10n/translate :view-on-stripe)
                :placement "right"}
   [:a {:href uri}
    [:span.icon.is-small
     [:i.fa.fa-cc-stripe]]]])

(defn payment-source-icon
  "Renders an icon to illustrate a given payment source (such as :bank, :visa, or :amex). Defaults to :bank"
  [source-type]
  [:span.panel-icon
   (case source-type
     :amex [:i.fa.fa-cc-amex]
     :visa [:i.fa.fa-cc-visa]
     [:i.fa.fa-university])])
