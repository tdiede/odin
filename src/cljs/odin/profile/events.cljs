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
                              [:property [:id :name :code]]]]]
               :on-success [:profile.fetch/success]
               :on-failure [:graphql/failure k]}}))

(def ^:private default-info {:first_name ""
                             :last_name  ""
                             :phone      ""})

(reg-event-db
 :profile.fetch/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [account        (get-in response [:data :account])
         personal-info  (select-keys account [:first_name :last_name :phone :email])
         emergency-info (select-keys (:emergency_contact account) [:first_name :last_name :phone])
         personal       (merge default-info personal-info)
         emergency      (merge default-info emergency-info)]
     (-> (assoc db :account account)
         (assoc-in [:contact :personal] {:current personal
                                         :new     personal})
         (assoc-in [:contact :emergency] {:current emergency
                                          :new     emergency})
         (assoc-in [:loading :profile/account] false)))))
