(ns odin.profile.subs
  (:require [odin.profile.db :as db]
            [odin.profile.payments.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::profile
 (fn [db _]
   (db/path db)))


(reg-sub
 :profile/account
 :<- [::profile]
 (fn [db _]
   (:account db)))


(reg-sub
 :profile.account/loading?
 :<- [::profile]
 (fn [db _]
   (get-in db [:loading :profile/account])))


(reg-sub
 :profile/security-deposit
 :<- [::profile]
 (fn [db _]
   (get-in db [:account :deposit])))
