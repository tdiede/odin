(ns admin.accounts.subs
  (:require [admin.accounts.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 ::accounts
 (fn [db _]
   (db/path db)))


(reg-sub
 :accounts/list
 :<- [::accounts]
 (fn [db _]
   (:accounts db)))


(reg-sub
 :accounts.list/loading?
 :<- [::accounts]
 (fn [db _]
   (get-in db [:loading :accounts/list])))
