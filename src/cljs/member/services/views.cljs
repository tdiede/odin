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
                                          (keyword (aget % "key"))])}
     [ant/menu-item {:key "book"} "Book services"]
     [ant/menu-item {:key "manage"} "Manage services"]]))


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
        selected (subscribe [:services.book/category])]
    [:div.catalogue
     [:div.columns {:style {:margin-bottom "0px"}}
      [:div.column.is-10
       [:h3.title.is-4 name]]
      (when (and (= @selected :all) (= (count items) 2))
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
  [ant/affix {:offsetBottom 20}
   [:div.has-text-right
    [ant/button
     {:size :large
      :type :primary
      :class "ant-btn-xl"}
     "Checkout - $50 (2)"]]
   #_[:div.shopping-cart
      [:div.columns
       [:div.column.is-7
        [:h4.subtitle.is-5 "X services being requested"]]
       [:div.column.is-1
        [:p.price (format/currency 150.0)]]
       [:div.column.is-4.has-text-right
        [ant/button "Submit Request"]]]]])


;; ==============================================================================
;; SHOPPING CART ================================================================
;; ==============================================================================


;; TODO for times and dates convert the moment string back into actual times and dates
;; TODO render form fields in the intended order
;; TODO make it look pretty?

(defn- column-fields-2 [fields]
  [:div
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.columns
       (for [field row]
         ^{:key (:id field)}
         [:div.column.is-half
          [:p (:label field)]
          [:p (:value field)]
          ])])
    (partition 2 2 nil fields))])


(defn cart-item-data [item]
  [:div
   [:hr]
   [column-fields-2 item]
   [ant/button "Edit Item"]])


(defn cart-item [item]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-3
      [:h4.subtitle.is-5 (get-in item [:service :title])]]
     [:div.column.is-6
      [:p.fs3 (get-in item [:service :description])]]
     [:div.column.is-1
      [:p.price (format/currency (get-in item [:service :price]))]]
     [:div.column.is-2
      [ant/button
       ;; on click must remove item from cart-items
       ;; {:on-click #(dispatch [:modal/show modal])}
       "Cancel"]]]
    (when (not-empty (:fields item))
      [cart-item-data (:fields item)])]]
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
  (let [cart-items (subscribe [:services.cart/requested-items])
        ;; first-item (first @cart-items)
        first-item (last @cart-items)
        ]
    [:div
     [:h1.title.is-3 "Shopping cart"]
     (doall
      (map-indexed #(with-meta [cart-item %2] {:key %1}) @cart-items))
     ]))



(defmethod content/view :services [route]
  [:div
   [services/service-modal
    {:action      "Add"
     :is-visible  @(subscribe [:modal/visible? db/modal])
     :service     @(subscribe [:services.add-service/adding])
     :form-fields @(subscribe [:services.add-service/form])
     :can-submit  @(subscribe [:services.add-service/can-submit?])
     :on-cancel   #(dispatch [:services.add-service/close])
     :on-submit   #(dispatch [:services.add-service/add])
     :on-change   #(dispatch [:services.add-service.form/update %1 %2])
     }]
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)])
