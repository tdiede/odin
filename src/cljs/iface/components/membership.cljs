(ns iface.components.membership
  (:require [antizer.reagent :as ant]
            [iface.utils.formatters :as format]
            [reagent.core :as r]))


(defn license-summary
  "A card representing the relevant details of a member's license."
  ([license]
   [license-summary license {:loading false}])
  ([{:keys [term rate starts ends property unit status] :as license} opts]
   [ant/card {:loading (:loading opts)
              :class   (str "is-flush"
                            (when-not (= :active status) " is-inactive"))}
    (when-not (nil? rate)
      [ant/card {:loading (:loading opts) :class "is-flush"}
       [:div.card-image
        [:figure.image
         [:img {:src (:cover_image_url property)}]]]
       [:div.card-content
        [:div.content
         [:h3.title.is-4 (str (:name property) " #" (:number unit))]
         [:h4.term
          (str term " months • " (str (format/date-short starts) " - " (format/date-short ends)))]
         [:p (str (format/currency rate) "/mo.")]
         (when-let [c (:content opts)]
           (r/as-element c))]]])]))
