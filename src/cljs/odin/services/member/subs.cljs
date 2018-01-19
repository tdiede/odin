(ns odin.services.member.subs
  (:require [odin.services.member.db :as db]
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
    {:id 2
     :code :laundry-services
     :title "Laundry Services"
     :services [{:id 1
                 :title "Single wash and fold"
                 :description "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus."
                 :price 25.0}
                {:id 2
                 :title "Wash and fold subscription"
                 :description "Aliquam erat volutpat. Proin quam nisl, tincidunt et, mattis eget, convallis nec, purus."
                 :price 50.0}
                {:id 3
                 :title "Dry Cleaning"
                 :description "Praesent fermentum tempor tellus. Phasellus purus."
                 :price 30.0}]}
     {:id 3
      :code :pet-services
      :title "Pet Services"
      :services [{:id 1
                  :title "Dog boarding"
                  :description "Etiam vel neque nec dui dignissim bibendum. Curabitur vulputate vestibulum lorem."
                  :price 50.0}
                 {:id 2
                  :title "Single Dog Walk"
                  :description "Sed bibendum. Vivamus id enim. Nullam tristique diam non turpis."
                  :price 10.0}
                 {:id 3
                  :title "Daily Dog Walk Subscription"
                  :description "Phasellus neque orci, porta a, aliquet quis, semper a, massa."
                  :price 50.0}]}]))
