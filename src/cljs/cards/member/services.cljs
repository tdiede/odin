(ns cards.member.services
  (:require [antizer.reagent :as ant]
            [cljsjs.moment]
            [devcards.core]
            [iface.components.services :as services]
            [member.services.db :as db]
            [reagent.core :as r])
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]]))


(def sample-data
  {:service {:id          1
             :title       "Single Dog Walk"
             :description "Aliquam posuere. Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."
             :price       15.0}
   :fields  [{:id       1
              :type     :date
              :key      :date
              :label    "Select day for dog walk"
              :required true}
             {:id       2
              :type     :time
              :key      :time
              :label    "Select time for dog walk"
              :required true}
             {:id       3
              :type     :desc
              :key      :desc
              :label    "Include any special instructions here."}
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
              :required true}]})

(defcard-doc
  "
  ## Sample Data

  This is an example of the data that we're working with."
  (dc/mkdn-pprint-source sample-data))


(def add-service-state-init
  {:form-fields (:fields sample-data)
   :cart        {}
   :loading     false})


(defonce add-service-state
  (r/atom add-service-state-init))


(defcard-rg add-service-form
  "The form rendered inside of the `add-service-modal`."
  (fn [data _]
    [services/add-service-form (:form-fields @data)
     {:on-change #(swap! data update :form-fields (fn [fields]
                                                    (map
                                                     (fn [field]
                                                       (if (= (:key field) %1)
                                                         (assoc field :value %2)
                                                         field))
                                                     fields)))}])
  add-service-state
  {:inspect-data true})


(defn get-from-service [key item]
  (get-in item [:service key]))


(defn on-submit [data]
  (fn [_]
    (swap! data assoc :loading true)
    (.setTimeout js/window
                 #(do
                    (swap! data update :cart conj {:service (get-in sample-data [:service :id])
                                                   :price   (get-in sample-data [:service :price])
                                                   :title   (get-in sample-data [:service :title])
                                                   :fields  (:form-fields @data)})
                    (swap! data assoc :form-fields (:fields sample-data))
                    (swap! data assoc :loading false))
                 1000)))


(defcard-rg add-service-modal-footer
  "Some docs over here....."
  (fn [data _]
    (let [can-submit (db/can-add-service? (:form-fields @data))]
      [services/add-service-modal-footer can-submit
       {:on-cancel  identity
        :on-submit  (on-submit data)
        :is-loading (:loading @data)}]))
  add-service-state
  {:inspect-data true})



(defcard-rg test-card
  "Something goes here"
  [:div
   [:h1 "This is text"]])
