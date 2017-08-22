(ns odin.profile.payments.subs
  (:require [odin.profile.payments.db :as db]
            [re-frame.core :refer [reg-sub]]))


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

(reg-sub
 :payment-sources/current
 :<- [::payments]
 (fn [db _]
   (:current-source db)))
