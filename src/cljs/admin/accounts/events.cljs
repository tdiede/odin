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
   {:db         (assoc-in db [:loading :accounts/list] true)
    :http-xhrio {:method          :get
                 :uri             "/api/accounts"
                 :response-format (ajax/transit-response-format)
                 :on-success      [:accounts.list/success]
                 :on-failure      [:accounts.list/failure]}}))


(reg-event-db
 :accounts.list/success
 [(path db/path)]
 (fn [db [_ response]]
   (tb/log response)
   (-> (assoc db :accounts (:accounts response))
       (assoc-in [:loading :accounts/list] false))))


(reg-event-db
 :accounts.list/failure
 (fn [db [_ response]]
   (tb/log response)
   (assoc db [:loading :accounts/list] false)))
