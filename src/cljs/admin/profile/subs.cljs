(ns admin.profile.subs
  (:require [admin.profile.db :as db]
            [admin.profile.membership.subs]
            [admin.profile.payments.subs]
            [admin.profile.contact.subs]
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
