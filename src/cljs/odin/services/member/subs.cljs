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
