(ns member.services.subs
  (:require [member.services.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :member.services/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :member.services/section
 :<- [:route/current]
 (fn [{page :page} _]
   (name page)))


(reg-sub
 :member.services.book/categories
 :<- [db/path]
 (fn [db _]
   [{:category :all
     :label    "All"}
    {:category :room-upgrades
     :label    "Room Upgrades"}
    {:category :laundry-services
     :label    "Laundry Services"}
    {:category :pet-services
     :label    "Pet Services"}]))


(reg-sub
 :member.services.book/category
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :category])))


(reg-sub
 :member.services.book.category/route
 :<- [:member.services/query-params]
 :<- [:route/current]
 (fn [[query-params route] [_ category]]
   (db/params->route (:page route)
                     (assoc query-params :category category))))


;; (reg-sub
;;  :member.services.add-service/add
;;  (fn [db _]
;;    (:service db)))


(reg-sub
 :member.services.add-service/currently-adding
 :<- [db/path]
 (fn [db _]
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
               :required true}]}))


(reg-sub
 :member.services.add-service/form
 :<- [db/path]
 (fn [db _]
   (:form-data db)))


(reg-sub
 :member.services.add-service/visible?
 :<- [:modal/visible? db/modal]
 (fn [is-visible _]
   is-visible))


(reg-sub
 :member.services.add-service/can-submit?
 :<- [db/path]
 (fn [db [_ fields]]
   (reduce (fn [all-defined field]
             (and all-defined (get (:form-data db) (:key field)))) true fields)))


(reg-sub
 :member.services.book/catalogues
 :<- [db/path]
 (fn [db _]
   [{:id       1
     :code     :room-upgrades
     :title    "Room upgrades"
     :services [{:id          1
                 :title       "Full-length Mirror"
                 :description "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus."
                 :price       25.0}
                {:id          2
                 :title       "Rug"
                 :description "Proin neque massa, cursus ut, gravida ut, lobortis eget, lacus.  Curabitur lacinia pulvinar nibh.  Donec at pede."
                 :price       50.0}
                {:id          3
                 :title       "Coffee Machine"
                 :description "Nunc eleifend leo vitae magna."
                 :price       125.00}]}
    {:id       2
     :code     :laundry-services
     :title    "Laundry Services"
     :services [{:id          1
                 :title       "Single wash and fold"
                 :description "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus."
                 :price       25.0}
                {:id          2
                 :title       "Wash and fold subscription"
                 :description "Aliquam erat volutpat. Proin quam nisl, tincidunt et, mattis eget, convallis nec, purus."
                 :price       50.0}
                {:id          3
                 :title       "Dry Cleaning"
                 :description "Praesent fermentum tempor tellus. Phasellus purus."
                 :price       30.0}]}
    {:id       3
     :code     :pet-services
     :title    "Pet Services"
     :services [{:id          1
                 :title       "Dog boarding"
                 :description "Etiam vel neque nec dui dignissim bibendum. Curabitur vulputate vestibulum lorem."
                 :price       50.0}
                {:id          2
                 :title       "Single Dog Walk"
                 :description "Sed bibendum. Vivamus id enim. Nullam tristique diam non turpis."
                 :price       10.0}
                {:id          3
                 :title       "Daily Dog Walk Subscription"
                 :description "Phasellus neque orci, porta a, aliquet quis, semper a, massa."
                 :price       50.0}]}]))


(reg-sub
 :member.services.cart/requested-items
 :<- [db/path]
 (fn [db _]
   [{:id          1
     :title       "Full-length Mirror"
     :description "Integer placerat tristique nisl. Nullam rutrum."
     :price       25.0
     :data        []}
    {:id          2
     :title       "Dog boarding"
     :description "Nunc rutrum turpis sed pede. Praesent augue. Nam euismod tellus id erat."
     :price       50.0
     :data        [{:label      "Start date"
                    :start-date "Feb 1, 2018"}
                   {:label    "End date"
                    :end-date "Feb 10, 2018"}
                   {:label    "Dog Size"
                    :dog-size :m}
                   {:label     "Additional information"
                    :more-info "He hates balls, do not play fetch"}]}
    {:id          3
     :title       "Single dog walk"
     :description "Nullam rutrum. Donec posuere augue in quam. Phasellus lacus."
     :price       10.0
     :data        [{:label "Date"
                    :date  "Feb 13, 2018"}
                   {:label "Time"
                    :time  "6:00 pm"}
                   {:label    "Dog size"
                    :dog-size :m}]}
    ]))
