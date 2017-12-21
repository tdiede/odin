(ns odin.accounts.subs
  (:require [odin.accounts.db :as db]
            [odin.accounts.admin.subs]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :accounts
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :accounts/norms)))


(reg-sub
 :account
 :<- [db/path]
 (fn [db [_ account-id]]
   (norms/get-norm db :accounts/norms account-id)))


(reg-sub
 :account/application
 :<- [db/path]
 (fn [db [_ account-id]]
   (:application (norms/get-norm db :accounts/norms account-id))))
