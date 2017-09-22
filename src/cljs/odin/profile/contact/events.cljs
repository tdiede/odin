(ns odin.profile.contact.events
  (:require [odin.profile.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   reg-sub
                                   path]]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :profile/contact [route]
  [[:profile/fetch-account (get-in route [:requester :id])]
   [:profile.contact.info.form/reset!]])




;; ========================================
;; Reset the contact form when we arrive on page


(reg-event-db
 :profile.contact.info.form/reset!
 (fn [db _]
   (assoc-in db [:contact] (:contact db/default-value))))


;; =============================================================================
;; Setters for Contact Info
;; =============================================================================


(reg-event-db
 :profile.contact.personal/update
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:contact :personal :new k] v)))


(reg-event-db
 :profile.contact.emergency/update
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:contact :emergency :new k] v)))



;; =============================================================================
;; Submit Contact Info
;; =============================================================================


(reg-event-fx
 :profile.contact.personal/submit!
 [(path db/path)]
 (fn [{:keys [db]} [_ new-info]]
   (let [account-id (get-in db [:account :id])]
     (tb/log account-id new-info)
     {:dispatch-n [[:loading :profile.contact.personal/submitting true]]
      :graphql    {:mutation [[:update_account {:id   account-id
                                                :data new-info} [:id]]]
                   :on-success [::save-personal-info-success]
                   :on-failure [::save-personal-info-fail]}})))


(reg-event-fx
 ::save-personal-info-success
 (fn [{:keys [db]} [_ response]]
   {:dispatch-n [[:loading :profile.contact.personal/submitting false]
                 [:notify/success "Information saved."]]}))


(reg-event-fx
 ::save-personal-info-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :profile.contact.personal/submitting false]
                 [:graphql/notify-errors! response]]}))


(reg-event-fx
 :profile.contact.emergency/submit!
 [(path db/path)]
 (fn [{:keys [db]} [_ new-info]]
   (let [account-id (get-in db [:account :id])
         data       {:emergency_contact new-info}]
     {:dispatch-n [[:loading :profile.contact.emergency/submitting true]]
      :graphql    {:mutation [[:update_account {:id   account-id
                                                :data data} [:id]]]
                   :on-success [::save-emergency-info-success]
                   :on-failure [::save-emergency-info-fail]}})))


(reg-event-fx
 ::save-emergency-info-success
 (fn [{:keys [db]} [_ response]]
   {:dispatch-n [[:loading :profile.contact.emergency/submitting false]
                 [:notify/success "Information saved."]]}))


(reg-event-fx
 ::save-emergency-info-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :profile.contact.emergency/submitting false]
                 [:graphql/notify-errors! response]]}))
