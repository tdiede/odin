(ns member.services.db
  (:require [member.routes :as routes]
            [clojure.string :as string]))


(def path ::services)


(def modal :member.services/add-service)


(def default-params
  {:category :all})


(def ^:private single-dog-walk-item
  {:service {:id          1
             :title       "Single Dog Walk"
             :description "Aliquam posuere. Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."
             :price       15.0}
   :fields [{:id       1
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
             :label    "Include any special instructions here."
             :required false}
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


(def default-value
  {path {:params     default-params
         ;; TODO:
         :catalogues [{:id    234
                       :name  "Room Upgrades"
                       :key   :room-upgrades
                       :items [single-dog-walk-item]}]
         :form-data  {}}})


(defmulti params->route (fn [page params] page))


(defmethod params->route :services/book [page params]
  (let [params (-> (select-keys params [:category])
                   (update :category name))]
    (routes/path-for page :query-params params)))


(defmethod params->route :services/manage [page params]
  (routes/path-for page))


(defmulti parse-query-params (fn [page params] page))


(defmethod parse-query-params :services/book [page params]
  (update params :category keyword))


(defmethod parse-query-params :services/manage [page params]
  params)


(defn can-add-service?
  [form-data fields]
  (->> (filter :required fields)
       (reduce
        (fn [acc field]
          (let [v (get form-data (:key field))]
            (and acc (if (string? v)
                       (not (string/blank? v))
                       v))))
        true)))
