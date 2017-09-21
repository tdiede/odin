(ns odin.profile.events
  (:require [odin.profile.db :as db]
            [odin.profile.contact.events]
            [odin.profile.payments.events]
            [odin.profile.membership.events]
            [odin.profile.settings.events]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(reg-event-fx
 :profile/fetch-account
 [(path db/path)]
 (fn [{:keys [db]} [_ account-id]]
    {:db      (assoc-in db [:loading :profile/account] true)
     :graphql {:query      [[:account {:id account-id}
                             [:id :first_name :last_name :email :phone
                              [:emergency_contact [:first_name :last_name :phone]]
                              [:deposit [:id :due :amount :amount_remaining :amount_paid :amount_pending]]
                              [:property [:id :name :code]]]]]
               :on-success [:profile.fetch/success]
               :on-failure [:profile.fetch/failure]}}))


(reg-event-db
 :profile.fetch/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [account (get-in response [:data :account])]
     (-> (assoc db :account account)
         (assoc :new-account account)
         (assoc-in [:loading :profile/account] false)))))


(reg-event-fx
 :profile.fetch/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :profile/account] false)
    :dispatch [:graphql/notify-errors! response]}))
