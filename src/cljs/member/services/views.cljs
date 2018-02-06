(ns member.services.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
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


;; -----------------------------------------------------------------------------------------------------------
;; Add service modal


(defn ante-meridiem? [x]
  (< x 12))


(defn number->time [n]
  (cond-> (js/moment)
    (= n (Math/floor n))    (.minute 0)
    (not= n (Math/floor n)) (.minute (* 60 (mod n 1)))
    true                    (.hour (Math/floor n))
    true                    (.second 0)))


(defn time-picker [{:keys [on-change] :or {on-change identity} :as props}]
  [ant/select (assoc props :on-change (comp on-change number->time js/parseFloat))
   (doall
    (for [t (reduce #(conj %1 %2 (+ %2 0.5)) [] (range 9 21))]
      (let [meridiem (if (ante-meridiem? t) "am" "pm")
            t'       (if (<= t 12.5) t (- t 12))]
        ^{:key (str t)}
        [ant/select-option {:value (str t)}
         (str (int (Math/floor t')) (if (integer? t) ":00" ":30") meridiem)])))])


(defn add-service-modal-footer [data fields]
  (let [required-fields (into [] (filter #(= true (:required %)) fields))
        can-submit      (subscribe [:member.services.add-service/can-submit? required-fields])]
    [:div
     [ant/button
      {:size     :large
       :on-click #(dispatch [:member.services.add-service/close modal])}
      "Cancel"]
     [ant/button
      {:type     :primary
       :size     :large
       :disabled (not @can-submit)
       :on-click #(dispatch [:member.services.add-service/add])
       ;; :on-click #(on-submit data)
       ;; :loading  @is-loading
       }
      "Add"]]))


(defn get-fields [fields type]
  (filter #(= type (:type %)) fields))


(defn- column-fields [fields component-fn]
  [:div
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.columns
       (for [field row]
         ^{:key (:id field)}
         [:div.column.is-half
          [ant/form-item {:label (:label field)}
           (r/as-element (component-fn field))]])])
    (partition 2 2 nil fields))])


(defmulti form-fields (fn [k data fields opts] k))


(defmethod form-fields :date [k data fields {on-change :on-change}]
  [column-fields (get-fields fields k)
   (fn [field]
     [ant/date-picker
      {:style         {:width "100%"}
       :value         (when-let [date (get data (:key field))]
                        (js/moment date))
       :on-change     #(on-change (:key field) (when-let [x %] (.toISOString x)))
       :disabled-date (fn [current]
                        (and current (< (.valueOf current) (.valueOf (js/moment.)))))
       :show-today    false}])])


(defmethod form-fields :time [k data fields {on-change :on-change}]
  [column-fields (get-fields fields k)
   (fn [field]
     [time-picker {:size      :large
                   ;; TODO:
                   ;; :value (when-let [time (get data (:key field))]
                   ;;          (js/moment time))
                   :on-change #(on-change (:key field) (.toISOString %))}])])


(defmethod form-fields :variants [k data fields {on-change :on-change}]
  [:div
   (map-indexed
    (fn [i field]
      ^{:key i}
      [:div.columns
       [:div.column
        [ant/form-item {:label (:label field)}
         [ant/radio-group
          {:value     (keyword (get data (:key field)))
           :on-change #(on-change (:key field) (.. % -target -value))}
          (map-indexed
           #(with-meta [ant/radio {:value (:key %2)} (:label %2)] {:key %1})
           (:options field))]]]])
    (get-fields fields k))])


(defmethod form-fields :desc [k data fields {on-change :on-change}]
  [:div
   (map-indexed
    (fn [i field]
      ^{:key i}
      [:div.columns
       [:div.column
        [ant/form-item {:label (:label field)}
         [ant/input
          {:type      :textarea
           :value     (get data (:key field))
           :on-change #(on-change (:key field) (.. % -target -value))}]]]])
    (get-fields fields k))])


(defn add-service-form
  [form-data fields opts]
  [:form
   [form-fields :date form-data fields opts]
   [form-fields :time form-data fields opts]
   [form-fields :variants form-data fields opts]
   [form-fields :desc form-data fields opts]])



(defn add-service-modal []
  (let [is-visible (subscribe [:modal/visible? :member.services/add-service])
        item       (subscribe [:member.services.add-service/currently-adding])
        form-data  (subscribe [:member.services.add-service/form])]
    [ant/modal
     {:title     (str "Add " (:title (:service @item)))
      :visible   @is-visible
      :on-cancel #(dispatch [:member.services.add-service/close modal])
      :footer    (r/as-element
                  [add-service-modal-footer form-data (:fields @item)])}
     [:div
      [:p (:description (:service @item))]
      [:br]
      [add-service-form @form-data (:fields @item)
       {:on-change #(dispatch [:member.services.add-service.form/update %1 %2])}]]]))


;; -----------------------------------------------------------------------------------------------------
;; SHOPPING CART
;; -----------------------------------------------------------------------------------------------------


(defn input-data [data-fields]
  [column-fields data-fields
   (fn [data-field]
     [:div.column.is-2
      [:p (:label data-field)]]
     [:div.column.is-4
      [:p (:start-date data-field)]])])


(defn cart-item-data [item]
  [:div
   [:hr]
   [input-data (get-fields :data item)]
   [ant/button "Edit Item"]])


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
    (when (not-empty (:data item))
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
   [add-service-modal]
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)
   ])
