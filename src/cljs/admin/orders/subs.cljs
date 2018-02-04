(ns admin.orders.subs
  (:require [admin.orders.db :as db]
            [iface.components.table :as table]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; generic ======================================================================
;; ==============================================================================


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


;; ==============================================================================
;; entry ========================================================================
;; ==============================================================================

(reg-sub
 :order/editing?
 :<- [db/path]
 (fn [db [_ order-id]]
   (get-in db [:order/editing order-id])))


;; ==============================================================================
;; list =========================================================================
;; ==============================================================================


(reg-sub
 :orders/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(def sort-fns
  {:price     table/number-sort-comp
   :created   table/date-sort-comp
   :billed_on table/date-sort-comp})


(reg-sub
 :orders/table
 :<- [:orders/query-params]
 :<- [:orders]
 (fn [[params orders] _]
   (table/sort-rows params sort-fns orders)))


(reg-sub
 :orders/statuses
 (fn [db _]
   [:all :pending :placed :fulfilled :failed :charged :canceled]))


(reg-sub
 :orders.statuses/selected
 :<- [:orders/query-params]
 (fn [params _]
   (:statuses params #{:all})))


(reg-sub
 :orders/members
 :<- [db/path]
 (fn [db _]
   (:accounts db)))


(reg-sub
 :orders.accounts/selected
 :<- [db/path]
 (fn [db _]
   (:selected-accounts db)))


(reg-sub
 :orders.filters/dirty?
 :<- [:orders/query-params]
 (fn [params _]
   (not= params db/default-params)))
