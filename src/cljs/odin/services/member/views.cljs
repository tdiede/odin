(ns odin.services.member.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [iface.typography :as typography]
            [toolbelt.core :as tb]
            [taoensso.timbre :as timbre]
            [odin.routes :as routes]
            [odin.services.member.db :as db]
            [odin.utils.formatters :as format]))

(def modal :member.services/add-service)


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
        can-submit      (reduce (fn [all-defined required-field]
                                  (and all-defined (get @data (:key required-field)))) true required-fields)
        ]
    [:div
     [ant/button
      {:size     :large
       :on-click #(dispatch [:member.services.add-service/close modal])}
      "Cancel"]
     [ant/button
      {:type     :primary
       :size     :large
       :disabled (not can-submit)
       ;; :on-click #(on-submit data)
       ;; :loading  @is-loading
       }
      "Add"]]))


(defn get-fields [type fields]
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


(defn date-fields [data fields]
  [column-fields fields
   (fn [field]
     [ant/date-picker
      {:style         {:width "100%"}
       :value         (when-let [date (get data (:key field))]
                        (js/moment date))
       :on-change     #(dispatch [:member.services.add-service.form/update
                                  (:key field) (.toISOString %)])
       :disabled-date (fn [current]
                        (and current (< (.valueOf current) (.valueOf (js/moment.)))))
       :show-today    false}])])


(defn time-fields [data fields]
  [column-fields fields
   (fn [field]
     [time-picker {:size      :large
                   ;; :value (when-let [time (get data (:key field))]
                   ;;          (js/moment time))
                   :on-change #(dispatch [:member.services.add-service.form/update
                                          (:key field) (.toISOString %)])}])])


(defn variants-fields [data fields]
  [:div
   (map-indexed
    (fn [i field]
      ^{:key i}
      [:div.columns
       [:div.column
        [ant/form-item {:label (:label field)}
         [ant/radio-group
          {:value     (keyword (get data (:key field)))
           :on-change #(dispatch [:member.services.add-service.form/update
                                  (:key field) (.. % -target -value)])
           ;; :on-change #(swap! data assoc (:key field) (.. % -target -value))
           }
          (map-indexed
           #(with-meta [ant/radio {:value (:key %2)} (:label %2)] {:key %1})
           (:options field))]]]])
    fields)])


(defn desc-fields [data fields]
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
           :on-change #(dispatch [:member.services.add-service.form/update
                                  (:key field) (.. % -target -value)])}]]]])
    fields)])


(defn add-service-modal []
  (let [is-visible (subscribe [:modal/visible? :member.services/add-service])
        item       (subscribe [:member.services.add-service/currently-adding])
        form-data  (subscribe [:member.services.add-service/form])
        fields     (:fields @item)
        data       (r/atom {})]
    (fn []
      (timbre/info :form-data @form-data)
      [ant/modal
       {:title     (str "Add " (:title (:service @item)))
        :visible   @is-visible
        :on-cancel #(dispatch [:member.services.add-service/close modal])
        :footer    (r/as-element [add-service-modal-footer form-data fields])}
       [:div
        [:p (:description (:service @item))]
        [:br]
        [:form
         [date-fields @form-data (get-fields :date fields)]
         [time-fields @form-data (get-fields :time fields)]
         [variants-fields @form-data (get-fields :variants fields)]
         [desc-fields @form-data (get-fields :desc fields)]]]])))

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
  [:div
   [:h3 "Here's your cart"]])


(defn view [route]
  [:div
   [add-service-modal]
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   (content route)
   ])
