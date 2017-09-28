(ns iface.media)


(defn step
  "Renders a media element with icon to the left (if provided). Use inside of a div.steps-vertical"
  [contents icon-type]
  [:article.media
   (when (string? icon-type)
     [:div.media-left
      [:span.icon.is-medium [:i.fa.fa-2x {:class (str "fa-" icon-type)}]]])
   [:div.media-content contents]])
