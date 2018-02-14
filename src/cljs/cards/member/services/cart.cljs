(ns cards.member.services.cart
  (:require [antizer.reagent :as ant]
            [cljsjs.moment]
            [devcards.core]
            [member.services.views :as view]
            [member.services.db :as db]
            [reagent.core :as r])
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]]))


;; TODO: Update given new format of data in cart
(def sample-services
  {:items '({:id          1
             :title       "Single Dog Walk"
             :description "Aliquam posuere. Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."
             :price       25.0}
            {:id          2
             :title       "Full length mirror"
             :description "Aliquam posuere. Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."
             :price       15.0})})


(def sample-data
  {:cart [{:service 1
           :fields  '({:id       1
                       :type     :date
                       :key      :date
                       :label    "Select day for dog walk"
                       :required true
                       :value    "2018-02-15T19:30:38.335Z"}
                      {:id       2
                       :type     :time
                       :key      :time
                       :label    "Select time for dog walk"
                       :required true
                       :value    "2018-02-13T20:00:00.328Z"}
                      {:id    3
                       :type  :desc
                       :key   :desc
                       :label "Include any special instructions here."}
                      {:id       4
                       :type     :variants
                       :key      :dog-size
                       :options  [{:key   :s
                                   :label "Small"}
                                  {:key   :m
                                   :label "Medium"}
                                  {:key   :l
                                   :label "Large"}]
                       :label    "Select your dog size:"
                       :required true
                       :value    "l"})}
          {:service 2}]})


(defcard-doc
  "
# Shopping cart items

## Sample data

This is an example of the data being sent to the shopping cart"
  (dc/mkdn-pprint-source sample-data)
  "
## Catalogue sample data

This is an example of the catalogue that the shopping cart will be referencing?"
  (dc/mkdn-pprint-source sample-services))


(defn get-service [id]
  (reduce
   (fn [service item]
     (if (= (:id item) id)
       (assoc service :service item)
       service))
   {} (:items sample-services)))


(defcard-rg shopping-cart-item
  (fn [_]
    (let [service-id (:service (nth (:cart sample-data) 1)) ;; gets service id from the second cart item in sample-data
          service    (get-service service-id)
          item       (assoc {} :service (:service service) :fields (:fields (second (:cart sample-data))))]
      [view/cart-item item])))


(defcard-rg shopping-cart-item-2
  (fn [_]
    (let [service-id (:service (nth (:cart sample-data) 0)) ;; gets service id from the first cart item in sample-data
          service    (get-service service-id)
          item       (assoc {} :service (:service service) :fields (:fields (first (:cart sample-data))))]
      [view/cart-item item])))
