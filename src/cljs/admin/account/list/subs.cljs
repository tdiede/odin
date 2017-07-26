(ns admin.account.list.subs
  (:require [admin.account.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 ::accounts
 (fn [db _]
   (db/path db)))


(reg-sub
 :accounts/list
 :<- [::accounts]
 (fn [db _]
   (let [accounts (get-in db [:accounts :list])]
     (map #(get-in db [:accounts :norms (:id %)]) accounts))))


(reg-sub
 :accounts.list/loading?
 :<- [::accounts]
 (fn [db _]
   (get-in db [:loading :accounts/list])))
