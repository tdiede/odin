(ns odin.orders.admin.subs
  (:require [odin.orders.admin.db :as db]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::orders
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin/orders
 :<- [::orders]
 (fn [db _]
   (norms/denormalize db :orders/norms)))
