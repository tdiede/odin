(ns odin.orders.subs
  (:require [odin.orders.db :as db]
            [odin.orders.admin.entry.subs]
            [odin.orders.admin.list.subs]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :orders
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :orders/norms)))


(reg-sub
 :order
 :<- [db/path]
 (fn [db [_ order-id]]
   (norms/get-norm db :orders/norms order-id)))
