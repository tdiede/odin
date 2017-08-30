(ns odin.account.list.events
  (:require [odin.routes :as routes]
            [odin.account.db :as db]
            [ajax.core :as ajax]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :account/list [_]
  [[:account.list/fetch]])


(reg-event-fx
 :account.list/fetch
 [(path db/path)]
 (fn [{:keys [db]} _]
   {:db      (assoc-in db [:loading :accounts/list] true)
    :graphql {:query      [[:accounts [:id :first_name :last_name :email :phone
                                       [:property [:id :name :code]]]]]
              :on-success [:accounts.list/success]
              :on-failure [:accounts.list/failure]}}))


(defn create-accounts-list
  "TODO:"
  [db accounts]
  (assoc-in db [:accounts :list]
            (map #(select-keys % [:id]) accounts)))


(defn merge-account-norms
  "TODO:"
  [db accounts]
  (let [existing-norms (get-in db [:accounts :norms])]
    (->> (reduce
          (fn [new-norms {:keys [id] :as new-account}]
            (let [old-account (get new-norms id)]
              (assoc new-norms id (merge old-account new-account))))
          existing-norms
          accounts)
         (assoc-in db [:accounts :norms]))))


(reg-event-db
 :accounts.list/success
 [(path db/path)]
 (fn [db [_ response]]
   ;;(tb/log response)
   (let [accounts (get-in response [:data :accounts])]
     (-> db
         (create-accounts-list accounts)
         (merge-account-norms accounts)
         (assoc-in [:loading :accounts/list] false)))))


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
   (let [account (rand-nth (get-in db [:accounts :list]))]
     {:db      (assoc-in db [:loading :accounts/list] true)
      :graphql {:mutation   [[:set_phone {:id    (:id account)
                                          :phone (rand-phone)}
                              [:id :phone]]]
                :on-success [:account.change-random-number/success]
                :on-failure [:account.change-random-number/failure]}})))


(reg-event-db
 :account.change-random-number/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [changed-acct (get-in response [:data :set_phone])]
     (-> (update-in db [:accounts :norms (:id changed-acct)] merge changed-acct)
         (assoc-in [:loading :accounts/list] false)))))


(reg-event-fx
 :account.change-random-number/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :accounts/list] false)
    :dispatch [:graphql/notify-errors! response]}))
