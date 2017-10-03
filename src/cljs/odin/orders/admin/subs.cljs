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


(reg-sub
 :admin.orders/query-params
 :<- [::orders]
 (fn [db _]
   (:params db)))


(reg-sub
 :admin.orders/statuses
 (fn [db _]
   [:all :pending :placed :fulfilled :charged :canceled]))


(reg-sub
 :admin.orders.statuses/selected
 :<- [:admin.orders/query-params]
 (fn [params _]
   (:statuses params #{:all})))
