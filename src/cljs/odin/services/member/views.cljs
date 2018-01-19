(ns odin.services.member.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [iface.typography :as typography]
            [toolbelt.core :as tb]
            [odin.routes :as routes]
            [odin.services.member.db :as db]
            [odin.utils.formatters :as format]))


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
    [:div.container.catalogue-menu
     [:div.columns
      (doall
       (map-indexed
        #(with-meta [category-icon %2] {:key %1})
        @categories))]]))


(defn service [service]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-3
      [:h4.subtitle.is-5 (:title service)]]
     [:div.column.is-6
      [:p.fs3 (:description service)]]
     [:div.column.is-1
      [:p.price (format/currency (:price service))]]
     [:div.column.is-2
      [ant/button "Request Service"]]]]])


(defn catalogue [{:keys [title services code] :as c}]
  (let [route (subscribe [:member.services.book.category/route code])
        selected (subscribe [:member.services.book/category])]
    [:div.catalogue
     [:div.columns {:style {:margin-bottom "0px"}}
      [:div.column.is-10
       [:h3.title.is-4 title]]
      (when (= @selected :all)
        [:div.column.is-2.has-text-right {:style {:display "table"}}
         [:a {:href @route
              :style {:display        "table-cell"
                      :vertical-align "middle"
                      :padding-top    8}}
          "See More"
          [ant/icon {:style {:margin-left 4} :type "right"}]]])]
     (doall
      (map-indexed #(with-meta [service %2] {:key %1}) services))]))


(defmulti content :page)


(defmethod content :services/book [_]
  (let [selected (subscribe [:member.services.book/category])
        catalogues (subscribe [:member.services.book/catalogues])
        c        (first (filter #(= @selected (:code %)) @catalogues))]
    [:div
     [categories]
     (if (= @selected :all)
       (doall
        (->> (map (fn [c] (update c :services #(take 2 %))) @catalogues)
             (map-indexed #(with-meta [catalogue %2] {:key %1}))))
       [catalogue c])]))


(defmethod content :services/manage [_]
  [:div
   [:h3 "Manage some services, yo"]])


(defn view [route]
  [:div
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)
   ])
