(ns odin.accounts.admin.list.subs
  (:require [odin.accounts.admin.list.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.accounts.list/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :admin.accounts.list/selected-role
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :selected-role])))


(reg-sub
 :admin.accounts/list
 :<- [:admin.accounts.list/query-params]
 :<- [:accounts]
 (fn [[params accounts] _]
   accounts))
