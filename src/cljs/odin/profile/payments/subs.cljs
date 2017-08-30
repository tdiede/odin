(ns odin.profile.payments.subs
  (:require [odin.profile.payments.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::payments
 (fn [db _]
   (db/path db)))

;; Payments
(reg-sub
 :payments
 :<- [::payments]
 (fn [db _]
   (:payments db)))

(reg-sub
 :payments.list/loading?
 :<- [::payments]
 (fn [db _]
   (get-in db [:loading :payments/list])))


;; Payment Sources (Linked Accounts)
(reg-sub
 :payment-sources
 :<- [::payments]
 (fn [db _]
   (:payment-sources db)))

(reg-sub
 :payment-sources.list/loading?
 :<- [::payments]
 (fn [db _]
   (get-in db [:loading :payment-sources/list])))

;; Yields the ID of currently-selected Source from DB
(reg-sub
 :payment-sources/current-source-id
 :<- [::payments]
 (fn [db _]
   (:current-source db)))

;; Yields the Source that corresponds to currently-selected Source ID
(reg-sub
 :payment-sources/current
 :<- [:payment-sources]
 :<- [:payment-sources/current-source-id]
 (fn [[sources source-id] _]
   (tb/find-by (fn [source] (= source-id (:id source))) sources)))

;; Yields the ID of currently-selected Source from DB
(reg-sub
 :payment-sources/new-account-type
 :<- [::payments]
 (fn [db _]
   (:new-account-type db)))
