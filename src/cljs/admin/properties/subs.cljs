(ns admin.properties.subs
  (:require [admin.properties.db :as db]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))



(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :properties/list
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :properties/norms)))


(reg-sub
 :property
 :<- [db/path]
 (fn [db [_ property-id]]
   (norms/get-norm db :properties/norms property-id)))


(reg-sub
 :property/units
 :<- [db/path]
 (fn [db [_ property-id]]
   (->> (norms/get-norm db :properties/norms property-id)
        :units
        (sort-by :number))))


(reg-sub
 :property.unit/rates
 :<- [db/path]
 (fn [db [_ property-id unit-id]]
   (get-in db [:unit-rates unit-id])))


(reg-sub
 :property.unit.rates/can-submit?
 :<- [db/path]
 (fn [db [_ property-id unit-id]]
   (let [unit (db/unit db property-id unit-id)]
     (not= (set (get-in db [:unit-rates unit-id]))
           (set (db/unit-rates unit))))))
