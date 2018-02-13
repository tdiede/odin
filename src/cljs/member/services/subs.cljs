(ns member.services.subs
  (:require [member.services.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :services/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :services/section
 :<- [:route/current]
 (fn [{page :page} _]
   (name page)))


(reg-sub
 :services.book/categories
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
 :services.book/category
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :category])))


(reg-sub
 :services.book.category/route
 :<- [:services/query-params]
 :<- [:route/current]
 (fn [[query-params route] [_ category]]
   (db/params->route (:page route)
                     (assoc query-params :category category))))


(reg-sub
 :services.add-service/adding
 :<- [db/path]
 (fn [db _]
   (:adding db)))


(reg-sub
 :services.add-service/form
 :<- [db/path]
 (fn [db _]
   (:form-data db)))


(reg-sub
 :services.add-service/visible?
 :<- [:modal/visible? db/modal]
 (fn [is-visible _]
   is-visible))


(reg-sub
 :services.add-service/required-fields
 :<- [:services.add-service/form]
 (fn [form _]
   (into [] (filter #(= true (:required %)) form))))


(reg-sub
 :services.add-service/can-submit?
 :<- [:services.add-service/form]
 :<- [:services.add-service/required-fields]
 (fn [[form required-fields] _]
   (reduce (fn [all-defined field]
             (and all-defined (:value field))) true required-fields)))


(reg-sub
 :services.book/catalogues
 :<- [db/path]
 (fn [db _]
   (:catalogues db)))


(reg-sub
 :services.cart/requested-items
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
