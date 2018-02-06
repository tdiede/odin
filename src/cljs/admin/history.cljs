(ns admin.history
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf :refer [reg-event-db
                                          reg-event-fx
                                          reg-sub]]
            [toolbelt.core :as tb]
            [taoensso.timbre :as timbre]))


(def path ::path)

;; ==============================================================================
;; events =======================================================================
;; ==============================================================================


(reg-event-db
 :history/bootstrap
 (fn [db _]
   (assoc db path {})))


(reg-event-fx
 :history/fetch
 (fn [{db :db} [k entity-id]]
   {:dispatch   [:ui/loading k true]
    :http-xhrio {:method          :get
                 :uri             (str "/api/history/" entity-id)
                 :response-format (ajax/transit-response-format)
                 :on-success      [::on-success k entity-id]
                 :on-failure      [::on-failure k]}}))



(reg-event-fx
 ::on-success
 [(rf/path path)]
 (fn [{db :db} [_ k entity-id response]]
   {:dispatch [:ui/loading k false]
    :db       (assoc db entity-id (get-in response [:data :history]))}))


(reg-event-fx
 ::on-failure
 (fn [_ [_ k response]]
   (timbre/error response)
   {:dispatch [:ui/loading k false]}))


;; ==============================================================================
;; subs =========================================================================
;; ==============================================================================


(reg-sub
 ::history
 (fn [db _]
   (path db)))


(reg-sub
 :history
 :<- [::history]
 (fn [db [_ entity-id]]
   (group-by :a (get db entity-id))))
