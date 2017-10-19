(ns odin.history.events
  (:require [odin.history.db :as db]
            [re-frame.core :refer [reg-event-fx path]]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]))


(reg-event-fx
 :history/fetch
 (fn [_ [k entity-id]]
   {:dispatch   [:loading k true]
    :http-xhrio {:method          :get
                 :uri             (str "/api/history/" entity-id)
                 :response-format (ajax/transit-response-format)
                 :on-success      [::on-success k entity-id]
                 :on-failure      [::on-failure k]}}))


(reg-event-fx
 ::on-success
 [(path db/path)]
 (fn [{db :db} [_ k entity-id response]]
   (tb/log response)
   {:dispatch [:loading k false]
    :db       (assoc db entity-id (get-in response [:data :history]))}))


(reg-event-fx
 ::on-failure
 (fn [_ [_ k response]]
   (tb/error response)
   {:dispatch [:loading k false]}))
