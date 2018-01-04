(ns odin.accounts.events
  (:require [odin.accounts.db :as db]
            [odin.accounts.admin.events]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-event-fx path]]
            [toolbelt.core :as tb]))


(defn- accounts-query-params
  [{:keys [roles q]}]
  (tb/assoc-when
   {}
   :roles (when-some [rs roles] (vec rs))
   :q q))


(reg-event-fx
 :accounts/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:accounts {:params (accounts-query-params params)}
                 [:id :name :email :phone :role :created
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
   {:db       (->> (get-in response [:data :accounts])
                   (norms/normalize db :accounts/norms))
    :dispatch [:loading k false]}))


(reg-event-fx
 :account/fetch
 [(path db/path)]
 (fn [{db :db} [k account-id opts]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:account {:id account-id}
                 [:id :name :email :phone :role :dob
                  [:deposit [:amount :due :status]]
                  [:property [:id :name]]
                  ;; TODO: Move to separate query
                  [:application [:id :move_in :created :updated :submitted :status :term :has_pet
                                 :approved_at
                                 [:approved_by [:id :name]]
                                 [:communities [:id :name]]
                                 [:fitness [:experience :skills :free_time :interested :dealbreakers :conflicts]]
                                 [:income [:id :uri :name]]
                                 [:pet [:type :breed :weight :sterile :vaccines :bitten :demeanor :daytime_care]]]]
                  ;; TODO: Move to separate query
                  [:licenses [:id :rate :starts :ends :term :status :rent_status
                              [:property [:id :cover_image_url :name]]

                              [:unit [:id :code :number]]]]]]]
               :on-success [::account-fetch k opts]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::account-fetch
 [(path db/path)]
 (fn [{db :db} [_ k opts response]]
   (let [account (get-in response [:data :account])]
     {:db         (norms/assoc-norm db :accounts/norms (:id account) account)
      :dispatch-n (tb/conj-when
                   [[:loading k false]]
                   (when-let [ev (:on-success opts)] (conj ev account)))})))
