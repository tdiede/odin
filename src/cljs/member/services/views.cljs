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


(def modal :member.services/add-service)


(defn menu []
  (let [section (subscribe [:member.services/section])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@section]
               :on-click      #(dispatch [:member.services.section/select
                                          (keyword (aget % "key"))])}
     [ant/menu-item {:key "book"} "Book services"]
     [ant/menu-item {:key "manage"} "Manage services"]]))


;; -----------------------------------------------------------------------------------------------------
;; BOOK SERVICES
;; -----------------------------------------------------------------------------------------------------


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
      [ant/button
       {:on-click #(dispatch [:modal/show modal])}
       "Request Service"]]]]])


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



;; (defn service-modal
;;   [{:keys [action is-visible currently-adding form-data can-submit on-cancel on-submit on-change]}]
;;   (.log js/console @form-data)
;;   [ant/modal
;;    {:title     (str action " " (get-in @currently-adding [:service :title]))
;;     :visible   @is-visible
;;     :on-cancel on-cancel
;;     :footer    (r/as-element
;;                 [add-service-modal-footer @can-submit
;;                  {:on-cancel on-cancel
;;                   :on-submit on-submit}])}
;;    [:div
;;     [:p (get-in @currently-adding [:service :description])]
;;     [:br]
;;     [add-service-form @form-data (:fields @currently-adding)
;;      {:on-change on-change}]]])


;; -----------------------------------------------------------------------------------------------------
;; SHOPPING CART
;; -----------------------------------------------------------------------------------------------------


(defn- column-fields-2 [fields component-fn]
  [:div
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.columns
       (for [field row]
         ^{:key (:id field)}
         ;; (.log js/console field " " row)
         [:div.column.is-half
          (r/as-element (component-fn field))
          #_[ant/form-item {:label (:label field)}
             (r/as-element (component-fn field))]])])
    (partition 2 2 nil fields))])


(defn input-data [data-fields]
  [column-fields-2 data-fields
   (fn [data-field]
     (let [label (name (key data-field))
           info ((keyword label) data-field)]
       (.log js/console label (nth data-field 1))
       [:div]))])

(comment
  #_[:div.column.is-2
     [:p (:label data-field)]]
  #_[:div.column.is-4
     [:p (:start-date data-field)]]
  )

(defn cart-item-data [item]
  [:div
   [:hr]
   [input-data (:form item)]
   [ant/button "Edit Item"]])


(defn test-div [word]
  [:div
   [:h1 word]])


(defn cart-item [item]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-3
      [:h4.subtitle.is-5 (:title item)]]
     [:div.column.is-6
      [:p.fs3 (:description item)]]
     [:div.column.is-1
      [:p.price (format/currency (:price item))]]
     [:div.column.is-2
      [ant/button
       ;; on click must remove item from cart-items
       ;; {:on-click #(dispatch [:modal/show modal])}
       "Cancel"]]]
    (when (not-empty (:form item))
      [cart-item-data item])]]
  )

;; -----------------------------------------------------------------------------------------------------
;; PREMIUM SERVICES CONTENT
;; -----------------------------------------------------------------------------------------------------


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
       [catalogue c])
     [shopping-cart]]))


(defmethod content :services/manage [_]
  [:div
   [:h3 "Manage some services, yo"]])


(defmethod content :services/cart [_]
  (let [cart-items (subscribe [:member.services.cart/requested-items])
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
    {:action           "Add"
     :is-visible       @(subscribe [:modal/visible? :member.services/add-service])
     :currently-adding @(subscribe [:member.services.add-service/currently-adding])
     :form-data        @(subscribe [:member.services.add-service/form])
     :can-submit       @(subscribe [:member.services.add-service/can-submit?])
     :on-cancel        #(dispatch [:member.services.add-service/close modal])
     :on-submit        #(dispatch [:member.services.add-service/add])
     :on-change        #(dispatch [:member.services.add-service.form/update %1 %2])
     }]
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)])
