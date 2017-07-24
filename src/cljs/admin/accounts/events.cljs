(ns admin.accounts.events
  (:require [admin.routes :as routes]
            [admin.accounts.db :as db]
            [ajax.core :as ajax]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :accounts [_]
  [[:accounts/list]])


(reg-event-fx
 :accounts/list
 [(path db/path)]
 (fn [{:keys [db]} _]
   {:db      (assoc-in db [:loading :accounts/list] true)
    :graphql {:query      [[:accounts [:id :first_name :last_name :email :phone]]]
              :on-success [:accounts.list/success]
              :on-failure [:accounts.list/failure]}}))


(reg-event-db
 :accounts.list/success
 [(path db/path)]
 (fn [db [_ response]]
   (tb/log response)
   (-> (assoc db :accounts (get-in response [:data :accounts]))
       (assoc-in [:loading :accounts/list] false))))


(reg-event-fx
 :accounts.list/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :accounts/list] false)
    :dispatch [:graphql/notify-errors! response]}))


(defn- rand-phone []
  (reduce
   #(str %1 (rand-int 10))
   ""
   (range 10)))


(reg-event-fx
 :account/change-random-phone!
 [(path db/path)]
 (fn [{:keys [db]} _]
   (let [account (rand-nth (:accounts db))]
     {:db      (assoc-in db [:loading :accounts/list] true)
      :graphql {:mutation [[:set_phone {:id (:id account) :phone (rand-phone)} [:id :phone]]]
                :on-success [:account.change-random-number/success]
                :on-failure [:account.change-random-number/failure]}})))


(reg-event-db
 :account.change-random-number/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [data (get-in response [:data :set_phone])]
     (-> (update db :accounts
                 (fn [accounts]
                   (mapv
                    (fn [account]
                      (if (= (:id data) (:id account))
                        (assoc account :phone (:phone data))
                        account))
                    accounts)))
         (assoc-in [:loading :accounts/list] false)))))


(reg-event-fx
 :account.change-random-number/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :accounts/list] false)
    :dispatch [:graphql/notify-errors! response]}))
