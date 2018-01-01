(ns odin.accounts.admin.entry.events
  (:require [odin.accounts.admin.entry.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin.accounts/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [[:account/fetch account-id]
     [:payments/fetch account-id]
     [:payment-sources/fetch account-id]]))


(reg-event-fx
 :admin.accounts.entry.approval/fetch-units
 (fn [_ [k property-id]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:units {:params {:property (tb/str->int property-id)}}
                 [:id :code :number
                  [:occupant [:name
                              [:active_license [:ends]]]]]]]
               :on-success [::fetch-units-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-units-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db       (assoc db :units (get-in response [:data :units]))
    :dispatch [:loading k false]}))


(reg-event-fx
 :admin.accounts.entry/approve
 [(path db/path)]
 (fn [{db :db} [k application-id {:keys [move-in unit term]}]]
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:approve_application {:application application-id
                                       :params      {:move_in (.toISOString move-in)
                                                     :unit    (tb/str->int unit)
                                                     :term    (tb/str->int term)}}
                 [:id [:account [:id]]]]]
               :on-success [::approve-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::approve-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (tb/log response)
   {:dispatch-n
    [[:loading k false]
     [:modal/hide :admin.accounts.approval/modal]
     [:account/fetch (get-in response [:data :approve_application :account :id])]]}))
