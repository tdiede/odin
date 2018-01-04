(ns odin.components.membership
  (:require [odin.l10n :as l10n]
            [odin.utils.formatters :as format]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


;; TODO: Spec
(defn license-summary
  "TODO:"
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


;; If a link to view PDF version of license is provided, show it here
;;(when-not (nil? (:view-link @license))
;;[:footer.card-footer
;; [:a.card-footer-item
;;  [:span.icon.is-small [:i.fa.fa-file-text]]
;;  [:span.with-icon "View Agreement"]]]])]))
