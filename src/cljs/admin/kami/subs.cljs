(ns admin.kami.subs
  (:require [admin.kami.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 ::kami
 (fn [db _]
   (db/path db)))


(reg-sub
 :kami/query
 :<- [::kami]
 (fn [db _]
   (:query db)))


(reg-sub
 :kami/selected-address
 :<- [::kami]
 (fn [db _]
   (:selected-address db)))


(reg-sub
 :kami/address-id
 :<- [:kami/selected-address]
 (fn [address _]
   (:eas_baseid address)))


(reg-sub
 :kami/query-params
 :<- [::kami]
 (fn [db _]
   (db/query-params db)))


(reg-sub
 :kami/addresses
 :<- [::kami]
 (fn [db _]
   (:addresses db)))


(reg-sub
 :kami/report
 :<- [::kami]
 (fn [db _]
   (:report db)))
