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
 (fn [{:keys [db]} [k account-id]]
   {:dispatch [:loading k true]
    :graphql  {:query      [[:account {:id account-id}
                             [:id :first_name :last_name :email :phone
                              [:emergency_contact [:first_name :last_name :phone]]
                              [:deposit [:id :due :amount :amount_remaining :amount_paid :amount_pending]]
                              [:property [:id :name :code]]]]]
               :on-success [:profile.fetch/success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 :profile.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ k response]]
   (let [account   (get-in response [:data :account])
         personal  (select-keys account [:first_name :last_name :phone])
         emergency (select-keys (:emergency_contact account) [:first_name :last_name :phone])]
     {:db       (-> (assoc db :account account)
                    (update-in [:contact :personal] merge {:current personal
                                                           :new     personal})
                    (update-in [:contact :emergency] merge {:current emergency
                                                            :new     emergency}))
      :dispatch [:loading k false]})))
