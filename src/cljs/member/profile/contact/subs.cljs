(ns member.profile.contact.subs
  (:require [member.profile.db :as db]
            [re-frame.core :refer [reg-sub subscribe]]
            [toolbelt.core :as tb]
            [clojure.data :refer [diff]]))

(reg-sub
 ::contact
 (fn [db _]
   (:contact (db/path db))))


;; =============================================================================
;; Personal contact details
;; =============================================================================


(reg-sub
  :profile.contact.personal/current
  :<- [::contact]
  (fn [db _]
    (get-in db [:personal :current])))


(reg-sub
  :profile.contact.personal/new
  :<- [::contact]
  (fn [db _]
    (get-in db [:personal :new])))


(reg-sub
  :profile.contact.personal/has-changes
  :<- [:profile.contact.personal/current]
  :<- [:profile.contact.personal/new]
  (fn [[current new] _]
    (not= current new)))


;; =============================================================================
;; Emergency Contact details
;; =============================================================================


(reg-sub
  :profile.contact.emergency/current
  :<- [::contact]
  (fn [db _]
    (get-in db [:emergency :current])))


(reg-sub
  :profile.contact.emergency/new
  :<- [::contact]
  (fn [db _]
    (get-in db [:emergency :new])))


(reg-sub
  :profile.contact.emergency/has-changes
  :<- [:profile.contact.emergency/current]
  :<- [:profile.contact.emergency/new]
  (fn [[current new] _]
    (not= current new)))
