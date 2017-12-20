(ns odin.orders.admin.list.subs
  (:require [odin.orders.admin.list.db :as db]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]
            [iface.table :as table]))

(reg-sub
 ::orders
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.orders/query-params
 :<- [::orders]
 (fn [db _]
   (:params db)))


(def sort-fns
  {:price     table/number-sort-comp
   :created   table/date-sort-comp
   :billed_on table/date-sort-comp})


(reg-sub
 :admin.table/orders
 :<- [:admin.orders/query-params]
 :<- [:orders]
 (fn [[params orders] _]
   (table/sort-rows params sort-fns orders)))


(reg-sub
 :admin.orders/statuses
 (fn [db _]
   [:all :pending :placed :fulfilled :failed :charged :canceled]))


(reg-sub
 :admin.orders.statuses/selected
 :<- [:admin.orders/query-params]
 (fn [params _]
   (:statuses params #{:all})))


(reg-sub
 :admin.orders/members
 :<- [::orders]
 (fn [db _]
   (:accounts db)))


(reg-sub
 :admin.orders.accounts/selected
 :<- [::orders]
 (fn [db _]
   (:selected-accounts db)))


(reg-sub
 :admin.orders.filters/dirty?
 :<- [:admin.orders/query-params]
 (fn [params _]
   (not= params db/default-params)))
