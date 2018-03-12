(ns admin.services.subs
  (:require [admin.services.db :as db]
            [admin.services.orders.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :services/list
 :<- [db/path]
 (fn [db _]
   (let [norms (norms/denormalize db :services/norms)]
     norms)))

(reg-sub
 :service
 :<- [db/path]
 (fn [db [_ service-id]]
   (norms/get-norm db :services/norms service-id)))

(reg-sub
 :services/range
 :<- [db/path]
 (fn [db _]
   [(:from db) (:to db)]))


(reg-sub
 :services/search-text
 :<- [db/path]
 (fn [db _]
   (:search-text db)))


(reg-sub
 :services/form
 :<- [db/path]
 (fn [db _]
   (:form db)))

(reg-sub
 :services.form/fields
 :<- [:services/form]
 (fn [form _]
   (:fields form)))
