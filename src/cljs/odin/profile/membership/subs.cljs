(ns odin.profile.membership.subs
  (:require [odin.profile.membership.db :as db]
            [re-frame.core :refer [reg-sub subscribe]]
            [toolbelt.core :as tb]))

(reg-sub
 ::membership
 (fn [db _]
   (db/path db)))


(reg-sub
 :member/license
 :<- [::membership]
 (fn [db _]
   (:license db)))


(reg-sub
 :member/rent-payments
 :<- [::membership]
 (fn [db [_ {:keys [status]}]]
   (let [payments (get-in db [:license :payments])]
     (if (some? status)
       (filter #(= status (:status %)) payments)
       payments))))


(reg-sub
 :member/upcoming-rent-payment
 :<- [::membership]
 (fn [db _]
   (let [payment (first (get-in db [:license :payments]))])
   (:license db)))
