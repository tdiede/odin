(ns iface.form
  (:require [antizer.reagent :as ant]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn item
  "An input form item."
  [{:keys [key form-item-props ant-id input-props rules initial-value type]
    :or   {type :input}}]
  (let [form (ant/get-form)]
    [ant/form-item form-item-props
     (ant/decorate-field form ant-id {:rules rules :initial-value initial-value}
                         (case type
                           :input [ant/input input-props]
                           [ant/input input-props]))]))


(defn items
  "Given `form-item` specs (TODO: currently undocumented), render a list of form
  items."
  [form-items & {:keys [on-change]}]
  (let [on-change (or on-change (fn [k] #(timbre/info k %)))]
    (map-indexed
     (fn [idx {key :key :as form-item}]
      (-> (assoc-in form-item [:input-props :on-change] (on-change key))
          (item)
          (with-meta {:key idx})))
    form-items)))
