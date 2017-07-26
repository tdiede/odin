(ns odin.account.entry.events
  (:require [odin.account.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :account/entry [{params :params}]
  [[:account/fetch (tb/str->int (:account-id params))]])


(reg-event-fx
 :account/fetch
 [(path db/path)]
 (fn [{:keys [db]} [_ account-id]]
   {:db      (assoc-in db [:loading :accounts/entry] true)
    :graphql {:query      [[:account {:id account-id}
                            [:id :first_name :last_name :email :phone
                             [:property [:id :name :code]]]]]
              :on-success [:account.fetch/success]
              :on-failure [:account.fetch/failure]}}))


(reg-event-db
 :account.fetch/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [account (get-in response [:data :account])]
     (assoc-in db [:accounts :norms (:id account)] account))))


(reg-event-fx
 :account.fetch/failure
 [(path db/path)]
 (fn [db [_ response]]
   (tb/error response)))
