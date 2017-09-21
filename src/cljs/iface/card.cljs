(ns iface.card
  (:require [reagent.core :as r]))


(defn selectable []
  (let [this                             (r/current-component)
        {:keys [class active] :as props} (r/props this)]
    [:div.selectable-card
     [:a.selectable-card-content
      (merge (dissoc props :class :active)
             {:class (str (when active "is-active") " " class)})
      (into [:div.selectable-card-item-info] (r/children this))]]))
