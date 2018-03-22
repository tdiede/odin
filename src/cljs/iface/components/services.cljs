(ns iface.components.services
  (:require [antizer.reagent :as ant]
            [cljsjs.moment]
            [iface.components.form :as form]
            [reagent.core :as r]))


;; -----------------------------------------------------------------------------------------------------------
;; Add service modal


(defn get-fields [fields type]
  (filter #(= type (:type %)) fields))


(defn- column-fields [fields component-fn]
  [:div
   (.log js/console "date: " fields)
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


(defmulti form-fields (fn [k fields opts] k))


;; why is this changing the value on both things?
(defmethod form-fields :date [k field {on-change :on-change}]
  [:div.columns
   [:div.column
    [ant/form-item {:label (:label field)}
     [ant/date-picker
      {:style         {:width "50%"}
       :value         (when-let [date (:value field)]
                        (js/moment date))
       :on-change     #(on-change (:index field) (when-let [x %] (.toISOString x)))
       :disabled-date (fn [current]
                        (and current (< (.valueOf current) (.valueOf (js/moment.)))))
       :show-today    false}]]]])


(defmethod form-fields :time [k field {on-change :on-change}]
  [:div.columns
   [:div.column
    [ant/form-item {:label (:label field)}
     [form/time-picker
      {:style       {:width "50%"}
       :size        :large
       :start       9
       :end         17
       :interval    45
       :placeholder "Select a time"
       :value       (when-let [time (:value field)]
                      (js/moment time))
       :on-change   #(on-change (:index field) (.toISOString %))}]]]])


;; Are we keeping variants?
#_(defmethod form-fields :variants [k fields {on-change :on-change}]
  [:div
   (map-indexed
    (fn [i field]
      ^{:key i}
      [:div.columns
       [:div.column
        [ant/form-item {:label (:label field)}
         [ant/radio-group
          {:value     (keyword (:value field))
           :on-change #(on-change (:key field) (.. % -target -value))}
          (map-indexed
           #(with-meta [ant/radio {:value (:key %2)} (:label %2)] {:key %1})
           (:options field))]]]])
    (get-fields fields k))])


(defmethod form-fields :text [k field {on-change :on-change}]
  [:div.columns
   [:div.column
    [ant/form-item {:label (:label field)}
     [ant/input
      {:style     {:width "100%"}
       :type      :textarea
       :value     (:value field)
       :on-change #(on-change (:index field) (.. % -target -value))}]]]])


;; TODO need to test this one
(defmethod form-fields :dropdown [k field {on-change :on-change}]
  [:div.columns
   [:div.column
    [ant/form-item {:label (:label field)}
     [ant/select
      {:style                {:width "50%"}
       :value     (:value field)
       :on-change #(on-change (:index field) %)}
      (doall
       (map-indexed
        #(with-meta [ant/select-option {:value %2} (:label %2)] {:key %1})
        (:options field)))]]]])


(defmethod form-fields :number [k field {on-change :on-change}]
  [:div.columns
   [:div.column
    [ant/form-item {:label (:label field)}
     [ant/input-number
      {:default-value 1
       :value         (:value field)
       :min           1
       :max           99
       :on-change     #(on-change (:index field) %)}]]]])


(defn add-service-form
  [fields opts]
  [:form
   (map-indexed
    #(with-meta [form-fields (keyword (name (:type %2))) %2 opts] {:key %1})
    fields)])


(defn add-service-modal-footer
  [action can-submit {:keys [on-cancel on-submit is-loading]}]
  [:div
   [ant/button
    {:size     :large
     :on-click on-cancel}
    "Cancel"]
   [ant/button
    {:type     :primary
     :size     :large
     :disabled (not can-submit)
     :on-click on-submit
     :loading  is-loading}
    action]])



(defn service-modal
  [{:keys [action is-visible service form-fields can-submit on-cancel on-submit on-change]}]
  [ant/modal
   {:title     (str action " " (:name service))
    :visible   is-visible
    :on-cancel on-cancel
    :footer    (r/as-element
                [add-service-modal-footer action can-submit
                 {:on-cancel on-cancel
                  :on-submit on-submit}])}
   [:div
    [:p (:description service)]
    [:br]
    [add-service-form form-fields
     {:on-change on-change}]]])
