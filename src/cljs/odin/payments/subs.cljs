(ns odin.payments.subs
  (:require [odin.payments.db :as db]
            [re-frame.core :refer [reg-sub]]
            [odin.utils.norms :as norms]))

(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :payments
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :payments/norms)))


(reg-sub
 :payments/by-account-id
 :<- [:payments]
 (fn [payments [_ account-id]]
   (filter #(= account-id (get-in % [:account :id])) payments)))
