(ns odin.accounts.admin.entry.subs
  (:require [odin.accounts.admin.entry.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.accounts.entry.approval/units
 :<- [db/path]
 (fn [db _]
   (->> (:units db)
        (sort-by :number))))
