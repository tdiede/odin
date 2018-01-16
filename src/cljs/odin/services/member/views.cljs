(ns odin.services.member.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [iface.typography :as typography]
            [toolbelt.core :as tb]
            [odin.routes :as routes]
            [odin.services.member.db :as db]))


(defn menu []
  (let [section (subscribe [:member.services/section])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@section]
               :on-click      #(dispatch [:member.services.section/select
                                          (keyword (aget % "key"))])}
     [ant/menu-item {:key "book"} "Book services"]
     [ant/menu-item {:key "manage"} "Manage services"]]))


(defn category-icon [{:keys [category label]}]
  (let [selected (subscribe [:member.services.book/category])
        route    (subscribe [:member.services.book.category/route category])]
    [:div.category-icon.column
     {:class (when (= category @selected) "is-active")}
     [:a {:href @route}
      [:img {:src "http://via.placeholder.com/150x150"}]
     [:p label]]]))


(defn categories []
  (let [categories (subscribe [:member.services.book/categories])]
    [:div.container
     [:div.columns
      (doall
       (map-indexed
        #(with-meta [category-icon %2] {:key %1})
        @categories))]]))


(defmulti content :page)


(defmethod content :services/book [_]
  [:div
   [categories]])


(defmethod content :services/manage [_]
  [:div
   [:h3 "Manage some services, yo"]])


(defn view [route]
  [:div
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)])
