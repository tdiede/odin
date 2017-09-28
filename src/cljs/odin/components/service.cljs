(ns odin.components.service
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Form
;; =============================================================================


(defn- price-range [prices]
  (str (apply min prices) "-" (apply max prices)))


(defn- price-text
  [{:keys [price billed quantity fields variants] :as item}]
  (let [prices (->> (conj (map :price variants) price)
                    (remove nil?)
                    (map (partial * quantity)))
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


(defn- form-item
  [item field {:keys [on-change] :or {on-change identity}}]
  (let [{:keys [label type key min max step]} field]
    [:div.control
     [:label.label {:style {:color "#6f6f6f" :font-size "0.75rem"}} label]
     (form-field item field on-change)]))


(defn- form
  [{:keys [service selected fields] :as item} {:keys [on-select]
                                               :or   {on-select identity}
                                               :as   opts}]
  (cond
    (not selected)
    [:div [ant/button {:type :ghost :on-click (fn [_] (on-select item))} "Select"]]

    (= 1 (count fields))
    [:div (form-item item (first fields) opts)]

    (> (count fields) 1)
    (let [pred   (comp (partial = :notes) :type)
          notes  (tb/find-by pred fields)
          fields (if (some? notes) (remove pred fields) fields)]
      [:div.columns
       (when (some? notes)
         [:div.column {:class (when-not (empty? fields) "is-half")}
          (form-item item notes opts)])
       (map-indexed
        #(with-meta
           [:div.column (form-item item %2 opts)]
           {:key %1})
        fields)])

    :else [:div]))


;; =============================================================================
;; API
;; =============================================================================


;; =============================================================================
;; Fields


(defn quantity-field
  [key value & {:keys [min max step] :or {min 1, step 1, max 100000}}]
  (tb/assoc-when
   {:type  :quantity
    :label "Quantity"
    :value value
    :key   key
    :min   min
    :step  step}
   :max max))


(defn notes-field [key value]
  {:key   key
   :label "Description"
   :value value
   :type  :notes})


(defn variants-field [key value]
  {:key   key
   :label "Variants"
   :value value
   :type  :variants})


(defn price-field
  [key value & {:keys [min max step] :or {min 1, step 1, max 100000}}]
  {:key   key
   :type  :price
   :label "Price"
   :value value
   :min   min
   :max   max
   :step  step})


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
