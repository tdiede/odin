(ns odin.components.ui
  (:require [odin.l10n :as l10n]
            [odin.utils.toolbelt :as utils]
            [odin.utils.formatters :as format]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))

(defn media-step
  "Renders a media element with icon to the left (if provided). Use inside of a div.steps-vertical"
  [contents icon-type]
  [:article.media
   (when (string? icon-type)
    [:div.media-left
     [:span.icon.is-medium [:i.fa {:class (str "fa-" icon-type)}]]])
   [:div.media-content contents]])
