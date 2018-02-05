(ns member.profile.subs
  (:require [member.profile.db :as db]
            [member.profile.membership.subs]
            [member.profile.payments.subs]
            [member.profile.contact.subs]
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
