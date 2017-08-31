(ns odin.profile.payments.subs
  (:require [odin.profile.payments.db :as db]
            [odin.profile.payments.sources.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::payments
 (fn [db _]
   (db/path db)))


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
