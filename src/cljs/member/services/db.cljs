(ns member.services.db
  (:require [member.routes :as routes]
            [clojure.string :as string]))


(defn rand-id []
  (js/parseInt
   (reduce
    (fn [acc _]
      (str acc (inc (rand-int 9))))
    ""
    (range 10))))


(def sample-services
  [{:id          (rand-id)
    :title       "Full-length Mirror"
    :description "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus."
    :price       25.0}
   {:id          (rand-id)
    :title       "Rug"
    :description "Proin neque massa, cursus ut, gravida ut, lobortis eget, lacus.  Curabitur lacinia pulvinar nibh.  Donec at pede."
    :price       50.0}
   {:id          (rand-id)
    :title       "Coffee Machine"
    :description "Nunc eleifend leo vitae magna."
    :price       125.00}
   {:id          (rand-id)
    :title       "Single wash and fold"
    :description "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus."
    :price       25.0}
   {:id          (rand-id)
    :title       "Wash and fold subscription"
    :description "Aliquam erat volutpat. Proin quam nisl, tincidunt et, mattis eget, convallis nec, purus."
    :price       50.0}
   {:id          (rand-id)
    :title       "Dry Cleaning"
    :description "Praesent fermentum tempor tellus. Phasellus purus."
    :price       30.0}
   {:id          (rand-id)
    :title       "Dog boarding"
    :description "Etiam vel neque nec dui dignissim bibendum. Curabitur vulputate vestibulum lorem."
    :price       50.0}
   {:id          (rand-id)
    :title       "Single Dog Walk"
    :description "Sed bibendum. Vivamus id enim. Nullam tristique diam non turpis."
    :price       10.0}
   {:id          (rand-id)
    :title       "Daily Dog Walk Subscription"
    :description "Phasellus neque orci, porta a, aliquet quis, semper a, massa."
    :price       50.0}])


(def sample-fields
  [{:index    0
    :id       (rand-id)
    :type     :date
    :key      :date
    :label    "Select day"
    :required true}
   {:index    1
    :id       (rand-id)
    :type     :time
    :key      :time
    :label    "Select time"
    :required true}
   {:index    3
    :id       (rand-id)
    :type     :desc
    :key      :desc
    :label    "Include any special instructions here."
    :required false}
   {:index    2
    :id       (rand-id)
    :type     :variants
    :key      :dog-size
    :options  [{:id    (rand-id)
                :key   :s
                :label "Small"}
               {:id    (rand-id)
                :key   :m
                :label "Medium"}
               {:id    (rand-id)
                :key   :l
                :label "Large"}]
    :label    "Select your size:"
    :required true}])


(defn rand-item []
  {:service (rand-nth sample-services)
   :fields  (take (inc (rand-int 4)) (shuffle sample-fields))})


(def sample-catalogues
  [{:id    (rand-id)
    :key   :room-upgrades
    :name  "Room Upgrades"
    :items (take (inc (rand-int 5)) (repeatedly rand-item))}
   {:id    (rand-id)
    :key   :laundry-services
    :name  "Laundry Services"
    :items (take (inc (rand-int 5)) (repeatedly rand-item))}
   {:id    (rand-id)
    :key   :pet-services
    :name  "Pet Services"
    :items (take (inc (rand-int 5)) (repeatedly rand-item))}])


;; ==================================================


(def path
  ::services)


(def modal
  :member.services/add-service)


(def default-params
  {:category :all})


(def default-value
  {path {:params     default-params
         :catalogues sample-catalogues
         :adding     nil
         :form-data  []}})


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
  [fields]
  (->> (filter :required fields)
       (reduce
        (fn [acc field]
          (let [v (:value field)]
            (and acc (if (string? v)
                       (not (string/blank? v))
                       v))))
        true)))
