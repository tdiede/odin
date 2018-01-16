(ns odin.services.member.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [iface.typography :as typography]
            [toolbelt.core :as tb]))


(defn menu []
  [ant/menu {:mode          :horizontal
             :selected-keys ["book"]
             :on-click      tb/log}
   [ant/menu-item {:key "book"} "Book services"]
   [ant/menu-item {:key "manage"} "Manage services"]])


(defn category-icon [label selected]
  [:div.category-icon.column
   {:class (when (= label @selected) "is-active")}
   [:a {:on-click #(reset! selected label)}
    [:img {:src "http://via.placeholder.com/150x150"}]
    [:p label]]])


(defn categories []
  (let [categories ["All" "Room Upgrades" "Laundry Services" "Pet Services"]
        selected   (r/atom (first categories))]
    (fn []
      [:div.container
       [:div.columns
       (doall
        (map-indexed
         #(with-meta [category-icon %2 selected] {:key %1})
         categories))]])))


(defn view [route]
  (let []
    [:div
     (typography/view-header "Premium Services" "Order and manage premium services.")
     [menu]
     [categories]]))
