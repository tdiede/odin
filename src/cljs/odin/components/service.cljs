(ns odin.components.service
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Form
;; =============================================================================


(defn- price-range [prices]
  (str (apply min prices) "-" (apply max prices)))


(defn price-text
  "The text-based representation of a service's price."
  [{:keys [price billed quantity fields variants] :as service}]
  (let [prices (->> (conj (map :price variants) price)
                    (remove nil?)
                    (map (partial * (or quantity 1))))
        suf    (if (= billed :monthly) "/month" "")]
    (cond
      (empty? prices)      "Quote"
      (= (count prices) 1) (str "$" (first prices) suf)
      :otherwise           (str "$" (price-range prices) suf))))


(defn- price
  [{:keys [rental selected] :as item} {:keys [on-delete]
                                       :or   {on-delete identity}}]
  [:span.tag
   {:class (if selected "is-success" "is-light")}
   (price-text item)
   (when selected
     [:button.delete.is-small
      {:type     :button
       :on-click (fn [_] (on-delete item))}])])


(defmulti form-field (fn [item field on-change] (:type field)))


(defmethod form-field :quantity
  [item {:keys [key min max step value]} on-change]
  [ant/input-number
   {:min           min
    :max           max
    :step          step
    :default-value value
    :autoFocus     true
    :on-change     #(on-change [(:service item) key %])}])


(defmethod form-field :notes
  [item {:keys [key value]} on-change]
  [ant/input
   {:type          :textarea
    :autoFocus     true
    :default-value value
    :on-change     #(on-change [(:service item) key (.. % -target -value)])}])


(defmethod form-field :price
  [item {:keys [key min max step value]} on-change]
  [ant/input-number
   {:min           min
    :max           max
    :step          step
    :default-value value
    :autoFocus     true
    :on-change     #(on-change [(:service item) key %])}])


(defn- variant-option [{:keys [id name price]}]
  [ant/radio {:value id}
   (if price
     (str name " ($" price ")")
     name)])


(defmethod form-field :variants
  [item {:keys [key value]} on-change]
  [ant/radio-group
   {:on-change     #(on-change [(:service item) key (.. % -target -value)])
    :default-value value}
   (map-indexed
    #(with-meta (variant-option %2) {:key %1})
    (:variants item))])


(defn- form-label [label]
  [:label.label {:style {:color "#6f6f6f" :font-size "0.75rem"}} label])


(defn- form-item
  [item field {:keys [on-change] :or {on-change identity}}]
  (let [{:keys [label type key min max step]} field]
    [:div.control
     (form-label label)
     (form-field item field on-change)]))


(defn- form
  [{:keys [service selected fields variants] :as item} {:keys [on-select on-change]
                                                        :or   {on-select identity}
                                                        :as   opts}]
  (let [fields (if-not (empty? variants)
                 (conj fields {:type      :variants
                               :key       :variant
                               :label     "Variants"
                               :col-class "is-two-thirds"
                               :value     (-> variants first :id)})
                 fields)]
    (cond
      (not selected)
      [:div [ant/button {:type :ghost :on-click (fn [_] (on-select item))} "Select"]]

      (> (count fields) 0)
      [:div.columns
       (map-indexed
        #(with-meta
           [:div.column
            {:class (:col-class %2)}
            (form-item item %2 opts)]
           {:key %1})
        fields)]

      :otherwise [:div])))


;; =============================================================================
;; API
;; =============================================================================


;; =============================================================================
;; Fields


(defn quantity-field
  [key value & {:keys [min max step] :or {min 1, step 1, max 100000}}]
  (tb/assoc-when
   {:type      :quantity
    :label     "Quantity"
    :col-class "is-one-third"
    :value     value
    :key       key
    :min       min
    :step      step}
   :max max))


(defn notes-field [key value]
  {:key       key
   :label     "Description"
   :col-class "is-two-thirds"
   :value     value
   :type      :notes})


(defn price-field
  [key value & {:keys [min max step] :or {min 1, step 1, max 100000}}]
  {:key       key
   :type      :price
   :label     "Price"
   :col-class "is-one-third"
   :value     value
   :min       min
   :max       max
   :step      step})


;; =============================================================================
;; Components


(defn card
  "Render a service as a card."
  [{:keys [name desc rental] :as item} opts]
  [ant/card {:class "svc" :bodyStyle {:padding "10px 16px"}}
   [:div.level.is-mobile
    {:style {:margin-bottom "0.75em"}}
    [:div.level-left
     [:div.level-item
      [:h4.svc-title
       {:style                   {:font-weight 600 :margin-bottom 0}
        :dangerouslySetInnerHTML {:__html (str name (if rental "<i> (rental)</i>" ""))}}]]]
    [:div.level-right.has-text-right
     [:div.level-item
      (price item opts)]]]

   [:p.svc-desc.fs2
    {:style                   {:margin-bottom "0.75em"}
     :dangerouslySetInnerHTML {:__html desc}}]

   [:div.svc-foot
    [form item opts]]])
