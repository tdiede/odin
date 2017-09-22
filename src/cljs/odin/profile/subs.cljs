(ns odin.profile.subs
  (:require [odin.profile.db :as db]
            [odin.profile.membership.subs]
            [odin.profile.payments.subs]
            [odin.profile.contact.subs]
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
 :profile/account-id
 :<- [::profile]
 (fn [db _]
   (get-in db [:account :id])))


(reg-sub
 :profile/account-mutable
 :<- [::profile]
 (fn [db _]
   (:new-account db)))


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
