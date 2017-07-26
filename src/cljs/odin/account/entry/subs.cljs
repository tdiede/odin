(ns odin.account.entry.subs
  (:require [odin.account.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::accounts
 (fn [db _]
   (db/path db)))


(reg-sub
 :account/entry
 :<- [::accounts]
 (fn [db [_ account-id]]
   (get-in db [:accounts :norms (tb/str->int account-id)])))
