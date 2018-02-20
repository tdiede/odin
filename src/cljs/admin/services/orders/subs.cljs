(ns admin.services.orders.subs
  (:require [admin.services.orders.db :as db]
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
 :services.order/editing?
 :<- [db/path]
 (fn [db [_ order-id]]
   (get-in db [:services.order/editing order-id])))


;; ==============================================================================
;; list =========================================================================
;; ==============================================================================


(reg-sub
 :services.orders/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(def sort-fns
  {:price     table/number-sort-comp
   :created   table/date-sort-comp
   :billed_on table/date-sort-comp})


(reg-sub
 :services.orders/table
 :<- [:services.orders/query-params]
 :<- [:orders]
 (fn [[params orders] _]
   (table/sort-rows params sort-fns orders)))


(reg-sub
 :services.orders/statuses
 (fn [db _]
   [:all :pending :placed :fulfilled :failed :charged :canceled]))


(reg-sub
 :orders.statuses/selected
 :<- [:services.orders/query-params]
 (fn [params _]
   (:statuses params #{:all})))


(reg-sub
 :services.orders/members
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
 :<- [:services.orders/query-params]
 (fn [params _]
   (not= params db/default-params)))
