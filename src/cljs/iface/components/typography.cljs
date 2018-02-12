(ns iface.components.typography)


(defn view-header
  "A header for a view with a `title` and optional `subtitle`."
  ([title]
   (view-header title nil))
  ([title subtitle]
   [:div.view-header
    [:h1.title.is-3
     {:dangerouslySetInnerHTML {:__html title}}]
    (when-some [s subtitle]
      [:p.subtitle.is-5 s])]))
