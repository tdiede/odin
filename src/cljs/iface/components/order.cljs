(ns iface.components.order
  (:require [antizer.reagent :as ant]
            [clojure.string :as string]
            [iface.utils.formatters :as format]
            [iface.utils.time :as time-fmt]
            [reagent.core :as r]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn status-icon
  "antd icon names that represent the status of an order."
  [status]
  (get {:pending   "clock-circle-o"
        :placed    "sync"
        :fulfilled "check-circle-o"
        :failed    "exclamation-circle-o"
        :charged   "credit-card"
        :canceled  "close-circle-o"}
       status))


(defn status-icon-class
  "antd icon classes that represent the status of an order"
  [status]
  (get
   {:placed    "has-text-info"
    :fulfilled "has-text-primary"
    :failed    "has-text-warning"
    :charged   "has-text-success"
    :canceled  "has-text-danger"}
   status))


(defn price-text
  [price billed quantity]
  (cond
    (string? price)     price
    (= :monthly billed) (str (format/currency (* price (or quantity 1))) "/month")
    :otherwise          (format/currency (* price (or quantity 1)))))


(defn- num-field [key value {:keys [on-change disabled]}]
  [ant/form-item {:label (-> key name string/capitalize)}
   [ant/input-number
    {:min       1
     :step      1
     :style     {:width "100%"}
     :value     value
     :disabled  disabled
     :on-change #(on-change key %)}]])


(defn- variant-option [{:keys [id name price]}]
  [ant/radio {:value id}
   (if price
     (str name " ($" price ")")
     name)])


(defn- line-item
  [line-items index {:keys [desc cost price] :as item} on-change]
  (letfn [(-update-val [k v]
            (on-change (assoc-in line-items [index k] v)))]
    [:div.columns.line-item
     [:div.column.is-6
      [ant/form-item {:label (when (zero? index) "Description")}
       [ant/input {:placeholder "Description"
                   :size        :small
                   :value       desc
                   :on-change   #(-update-val :desc (.. % -target -value))}]]]
     [:div.column.is-2
      [ant/form-item {:label (when (zero? index) "Cost")}
       [ant/input-number {:min         0
                          :step        1
                          :size        :small
                          :placeholder "Cost"
                          :value       cost
                          :on-change   (partial -update-val :cost)}]]]
     [:div.column.is-2
      [ant/form-item {:label (when (zero? index) "Price")}
       [ant/input-number {:min         0
                          :step        1
                          :size        :small
                          :placeholder "Price"
                          :value       price
                          :on-change   (partial -update-val :price)}]]]
     [:div.column.is-2
      [ant/form-item {:label (when (zero? index) "Delete")}
       [ant/button {:type     :danger
                    :shape    :circle
                    :icon     "close-circle-o"
                    :size     :small
                    :on-click #(on-change (tb/remove-at line-items index))}]]]]))


(defn- order-price
  [{:keys [variants] :as service} {:keys [price variant line_items] :as form}]
  (let [vprice (:price (tb/find-by (comp (partial = variant) :id) variants))
        fprice (when-not (or (zero? price) (string/blank? price)) price)
        lprice (when-not (empty? line_items) (reduce #(+ %1 (:price %2)) 0 line_items))]
    (or lprice fprice vprice (:price service))))


(defn- line-item-cost
  [line-items]
  (->> line-items
       (map
        (fn [{cost :cost}]
          (if (or (nil? cost) (string/blank? cost)) 0 cost)))
       (apply +)))


(defn- order-cost
  [{:keys [variants] :as service} {:keys [line_items cost variant]}]
  (let [lcost (line-item-cost line_items)
        vcost (:cost (tb/find-by (comp (partial = variant) :id) variants))]
    (or (when-not (zero? lcost) lcost) cost vcost (:cost service))))


(defmulti order-form-svc-field
  (fn [field on-change]
    (:type field)))


(defmethod order-form-svc-field :date
  [{:keys [id index label required]} on-change]
  [ant/form-item
   {:label label}
   [ant/date-picker
    {:on-change #(on-change [:fields index] {:service_field id
                                            :value         (time-fmt/moment->iso %)})}]])


(defmethod order-form-svc-field :time
  [{:keys [id index label required]} on-change]
  [ant/form-item
   {:label label}
   [ant/time-picker
    {:use-12-hours true
     :format       "HH:mm"
     :on-change    #(on-change [:fields index] {:service_field id
                                                :value         (time-fmt/moment->iso %)})}]])


(defmethod order-form-svc-field :dropdown
  [{:keys [id index label required options]} on-change]
  [ant/form-item
   {:label label}
   [ant/select
    {:style {:width "50%"}
     :on-change #(on-change [:fields index] {:service_field id
                                             :value         %})}
    (map (fn [{:keys [index value label]}]
           (with-meta
             [ant/select-option
              {:value value}
              label]
             {:key index}))
         options)]])


(defmethod order-form-svc-field :number
  [{:keys [id index label required]} on-change]
  [ant/form-item
   {:label label}
   [ant/input
    {:type      :number
     :style     {:width "30%"}
     :on-change #(on-change [:fields index] {:service_field id
                                             :value         (.. % -target -value)})}]])


(defmethod order-form-svc-field :default
  [{:keys [id index label required] :as field} on-change]
  [ant/form-item
   {:label label}
   [ant/input
    {:type :text
     :on-change #(on-change [:fields index] {:service_field id
                                             :value (.. % -target -value)})}]])


(defn- render-order-form-svc-fields
  [field on-change]
  (with-meta
    [order-form-svc-field field on-change]
    {:key (:id field)}))


(defn- order-form
  [svc order {:keys [on-change] :or {on-change #(timbre/info %)}}]
  (let [{:keys [quantity cost request variant summary line_items]
         :or   {quantity 1}} order]
    [:div
     ;; quantity, price, cost
     [:div.columns
      [:div.column
       (num-field :quantity quantity {:on-change on-change})]
      [:div.column
       (num-field :cost (order-cost svc order)
                  {:on-change on-change
                   :disabled  (pos? (line-item-cost line_items))})]
      [:div.column
       (num-field :price (order-price svc order)
                  {:on-change on-change
                   :disabled  (not (empty? line_items))})]]

     ;; variants
     (when-not (empty? (:variants svc))
       [ant/form-item {:label "Variants"}
        [ant/radio-group
         {:on-change #(on-change :variant (.. % -target -value))
          :value     variant}
         (map-indexed
          #(with-meta (variant-option %2) {:key %1})
          (:variants svc))]])


     (when-not (empty? (:fields svc))
       (map #(render-order-form-svc-fields % on-change) (:fields svc)))

     [ant/form-item
      {:label "Additional Request Notes"
       :help  "Information provided to us by the customer when the order was requested."}
      [ant/input
       {:type      :textarea
        :value     (when-some [s request] (format/unescape-newlines s))
        :on-change #(on-change :request (format/escape-newlines (.. % -target -value)))}]]

     [ant/form-item
      {:label "Fulfillment Notes"
       :help  "Information pertaining to the fulfillment of this order."}
      [ant/input
       {:type      :textarea
        :value     (when-some [s summary] (format/unescape-newlines summary))
        :on-change #(on-change :summary (format/escape-newlines (.. % -target -value)))}]]

     (when-not (empty? line_items)
       [:div
        (map-indexed
         #(with-meta [line-item line_items %1 %2 (comp (partial on-change :line_items) vec)] {:key %1})
         line_items)])

     [:div.has-text-right
      [ant/button
       {:size     :small
        :icon     "plus"
        :on-click #(on-change :line_items (vec (conj line_items {})))}
       "Add Line Item"]]]))


(defn line-items-valid?
  "Are `line-items` in a state compatible with persisting them?"
  [line-items]
  (every?
   (fn [{:keys [price desc]}]
     (and (number? price) (not (neg? price)) (not (string/blank? desc))))
   line-items))


(defn form
  "A form used to manipulate order fields during creation/modification."
  [{:keys [name desc] :as svc} order opts]
  (let [{:keys [price billed quantity]} order]
    [:div
     [:div.level.is-mobile
      {:style {:margin-bottom "0.75em"}}
      [:div.level-left
       [:div.level-item
        [:h4.svc-title
         {:style                   {:font-weight 600 :margin-bottom 0}
          :dangerouslySetInnerHTML {:__html name}}]]]
      [:div.level-right.has-text-right
       [:div.level-item
        [:span.tag.is-success
         (price-text (or (order-price svc order) "Quote") billed quantity)]]]]

     [:p.svc-desc.fs2
      {:style                   {:margin-bottom "0.75em"}
       :dangerouslySetInnerHTML {:__html desc}}]

     [:div.svc-foot
      [order-form svc order opts]]]))
