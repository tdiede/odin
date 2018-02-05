(ns admin.profile.contact.events
  (:require [admin.profile.db :as db]
            [admin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   reg-sub
                                   path]]
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
     {:dispatch [:ui/loading k true]
      :graphql  {:mutation   [[:update_account {:id   account-id
                                                :data (dissoc new-info :email)}
                               [:id :name :phone]]]
                 :on-success [::save-personal-info-success k]
                 :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::save-personal-info-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:user/update (get-in response [:data :update_account])]
                 [:notify/success "Information saved."]]}))


(reg-event-fx
 :profile.contact.emergency/submit!
 [(path db/path)]
 (fn [{:keys [db]} [_ new-info]]
   (let [account-id (get-in db [:account :id])
         data       {:emergency_contact new-info}
         k          :profile.contact.emergency/submitting]
     {:dispatch-n [[:ui/loading k true]]
      :graphql    {:mutation   [[:update_account {:id   account-id
                                                  :data data} [:id]]]
                   :on-success [::save-emergency-info-success k]
                   :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::save-emergency-info-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:notify/success "Information saved."]]}))
