(ns odin.accounts.events
  (:require [odin.accounts.db :as db]
            [odin.accounts.admin.events]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-event-fx path]]
            [toolbelt.core :as tb]))


(defn- accounts-query-params
  [{:keys [roles]}]
  (tb/assoc-when
   {}
   :roles (when-some [rs roles] (vec rs))))


(reg-event-fx
 :accounts/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:accounts {:params (accounts-query-params params)}
                 [:id :name :email :phone :role
                  [:property [:id :name]]
                  [:application [:id :move_in :created :updated :submitted :status
                                 [:communities [:id :name]]]]
                  [:active_license [:id :rate :starts :ends :term :status :rent_status
                                    [:unit [:id :code :number]]]]
                  [:deposit [:id :amount :due :amount_remaining :amount_paid :amount_pending]]]]]
               :on-success [::accounts-query k params]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::accounts-query
 [(path db/path)]
 (fn [{db :db} [_ k params response]]
   (tb/log response)
   {:db       (->> (get-in response [:data :accounts])
                   (norms/normalize db :accounts/norms))
    :dispatch [:loading k false]}))
