(ns iface.loading)


(def ^:private size->class
  {:large    "is-large"
   :fullpage "is-fullheight"})


(defn fullpage
  [& {:keys [size text]
      :or   {size :large
             text "Loading..."}}]
  [:section.hero {:class (size->class size)}
   [:div.hero-body
    [:div.container.has-text-centered
     [:h1.is-3.subtitle text]
     [:div.sk-double-bounce
      [:div.sk-child.sk-double-bounce1]
      [:div.sk-child.sk-double-bounce2]]]]])
