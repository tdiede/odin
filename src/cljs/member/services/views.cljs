(ns member.services.views
  (:require [antizer.reagent :as ant]
            [iface.components.form :as form]
            [iface.components.services :as services]
            [iface.components.typography :as typography]
            [iface.utils.formatters :as format]
            [member.content :as content]
            [member.routes :as routes]
            [member.services.db :as db]
            [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn menu []
  (let [section (subscribe [:services/section])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@section]
               :on-click      #(dispatch [:services.section/select
                                          (aget % "key")])}
     [ant/menu-item {:key "book"} "Book services"]
     [ant/menu-item {:key "manage"} "Manage services"]
     #_[ant/menu-item {:key "cart"} "Shopping cart"]]))


;; ==============================================================================
;; BOOK SERVICES ================================================================
;; ==============================================================================


(defn category-icon [{:keys [category label]}]
  (let [selected (subscribe [:services.book/category])
        route    (subscribe [:services.book.category/route category])]
    [:div.category-icon.column
     {:class (when (= category @selected) "is-active")}
     [:a {:href @route}
      [:img {:src "http://via.placeholder.com/150x150"}]
      [:p label]]]))


(defn categories []
  (let [categories (subscribe [:services.book/categories])]
    [:div.container.catalogue-menu
     [:div.columns
      (doall
       (map-indexed
        #(with-meta [category-icon %2] {:key %1})
        @categories))]]))


(defn catalogue-item [{:keys [service] :as item}]
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
      [ant/button
       {:on-click #(dispatch [:services.add-service/show item])}
       "Request Service"]]]]])


(defn catalogue [{:keys [id name items key] :as c}]
  (let [route    (subscribe [:services.book.category/route key])
        selected (subscribe [:services.book/category])
        has-more (subscribe [:services.book.category/has-more? id])]
    [:div.catalogue
     [:div.columns {:style {:margin-bottom "0px"}}
      [:div.column.is-10
       [:h3.title.is-4 name]]
      (when (and (= @selected :all) @has-more)
        [:div.column.is-2.has-text-right {:style {:display "table"}}
         [:a {:href  @route
              :style {:display        "table-cell"
                      :vertical-align "middle"
                      :padding-top    8}}
          "See More"
          [ant/icon {:style {:margin-left 4} :type "right"}]]])]
     (doall
      (map-indexed #(with-meta [catalogue-item %2] {:key %1}) items))]))


(defn shopping-cart []
  (let [item-count (subscribe [:services.cart/item-count])
        total-cost (subscribe [:services.cart/total-cost])]
    [ant/affix {:offsetBottom 20}
     [:div.has-text-right
      [ant/button
       {:size :large
        :type :primary
        :class "ant-btn-xl"
        :on-click #(dispatch [:services.section/select "cart"])}
       "Checkout - $" @total-cost " (" @item-count ")"]]]))


;; ==============================================================================
;; SHOPPING CART ================================================================
;; ==============================================================================


;; TODO for times and dates convert the moment string back into actual times and dates
;; TODO render form fields in the intended order
;; TODO make it look pretty?


(defmulti field-value (fn [k value options] k))


(defmethod field-value :time [k value options]
  [:span
   [:p.fs3 (format/time-short value)]])


(defmethod field-value :date [k value options]
  [:span
   [:p.fs3 (format/date-short value)]])


(defmethod field-value :variants [k value options]
  (let [vlabel (reduce (fn [v option] (if (= (keyword value) (:key option)) (:label option) v)) nil options)]
    [:span
     [:p.fs3 vlabel]]))


(defmethod field-value :desc [k value options]
  [:span
   [:p.fs3 value]])


(defn- column-fields-2 [fields]
  [:div
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.columns
       (for [{:keys [id type label value options]} row]
         ^{:key (:id id)}
         [:div.column.is-half
          [:div
           [:span
            [:p.fs3.bold label]]
           [field-value type value options]]])])
    (partition 2 2 nil fields))])


(defn cart-item-data [fields service-item]
  [:div.cart-item
   [:hr]
   [column-fields-2 fields]
   [ant/button {:style {:margin-top "15px"}
                :icon "edit"
                :on-click #(dispatch [:services.cart.item/edit service-item fields])}
    "Edit Item"]])


(defn cart-item [{:keys [id title description price fields]}]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-9
      [:h4.subtitle.is-5 title]]
     #_[:div.column.is-6
      [:p.fs3 description]]
     [:div.column.is-1
      [:p.price (format/currency price)]]
     [:div.column.is-2.align-right
      [ant/button
       {:type     "danger"
        :icon     "close"
        :on-click #(dispatch [:services.cart.item/remove id])}
       ;; on click must remove item from cart-items
       ;; {:on-click #(dispatch [:modal/show modal])}
       "Remove item"]]]
    (when-not (empty? fields)
      [cart-item-data (sort-by :index fields) {:id id
                                               :title               title
                                               :description         description
                                               :price               price}])]]
  )

;; ==============================================================================
;; PREMIUM SERVICES CONTENT =====================================================
;; ==============================================================================



(defmulti content :page)


(defmethod content :services/book [_]
  (let [selected   (subscribe [:services.book/category])
        catalogues (subscribe [:services.book/catalogues])
        c          (first (filter #(= @selected (:key %)) @catalogues))]
    [:div
     [services/service-modal
      {:action      "Add"
       :is-visible  @(subscribe [:services.add-service/visible?])
       :service     @(subscribe [:services.add-service/adding])
       :form-fields @(subscribe [:services.add-service/form])
       :can-submit  @(subscribe [:services.add-service/can-submit?])
       :on-cancel   #(dispatch [:services.add-service/close])
       :on-submit   #(dispatch [:services.add-service/add])
       :on-change   #(dispatch [:services.add-service.form/update %1 %2])}]
     [categories]
     (if (= @selected :all)
       (doall
        (->> (map (fn [c] (update c :items #(take 2 %))) @catalogues)
             (map-indexed #(with-meta [catalogue %2] {:key %1}))))
       [catalogue c])
     [shopping-cart]]))


(defmethod content :services/manage [_]
  [:div
   [:h3 "Manage some services, yo"]])


(defmethod content :services/cart [_]
  (let [cart-items (subscribe [:services.cart/cart])
        ;; first-item (first @cart-items)
        first-item (last @cart-items)
        ]
    [:div
     [services/service-modal
      {:action      "Edit"
       :is-visible  @(subscribe [:services.add-service/visible?])
       :service     @(subscribe [:services.add-service/adding])
       :form-fields @(subscribe [:services.add-service/form])
       :can-submit  @(subscribe [:services.add-service/can-submit?])
       :on-cancel   #(dispatch [:services.add-service/close])
       :on-submit   #(dispatch [:services.cart.item/save-edit])
       :on-change   #(dispatch [:services.add-service.form/update %1 %2])}]
     [:h1.title.is-3 {:style {:margin-top "25px"}} "Shopping cart"]
     (doall
      (map-indexed #(with-meta [cart-item %2] {:key %1}) @cart-items))]))



(defmethod content/view :services [route]
  [:div
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)])
