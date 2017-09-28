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
   (let [account-id (get-in db [:account :id])
         k          :profile.contact.personal/submitting]
     {:dispatch [:loading k true]
      :graphql  {:mutation   [[:update_account {:id   account-id
                                                :data new-info}
                               [:id :name :phone]]]
                 :on-success [::save-personal-info-success k]
                 :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::save-personal-info-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:account/update (get-in response [:data :update_account])]
                 [:notify/success "Information saved."]]}))


(reg-event-fx
 :profile.contact.emergency/submit!
 [(path db/path)]
 (fn [{:keys [db]} [_ new-info]]
   (let [account-id (get-in db [:account :id])
         data       {:emergency_contact new-info}
         k          :profile.contact.emergency/submitting]
     {:dispatch-n [[:loading k true]]
      :graphql    {:mutation   [[:update_account {:id   account-id
                                                  :data data} [:id]]]
                   :on-success [::save-emergency-info-success k]
                   :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::save-emergency-info-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:notify/success "Information saved."]]}))
