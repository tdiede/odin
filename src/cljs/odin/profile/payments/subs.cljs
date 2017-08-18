(ns odin.profile.payments.subs
  (:require [odin.profile.payments.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 ::payments
 (fn [db _]
   (db/path db)))


(reg-sub
 :payments
 :<- [::payments]
 (fn [db _]
   (:payments db)))
