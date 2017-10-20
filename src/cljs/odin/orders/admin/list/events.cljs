(ns odin.orders.admin.list.events
  (:require [odin.orders.admin.list.db :as db]
            [odin.orders.db :as orders-db]
            [odin.utils.norms :as norms]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin/orders [{params :params}]
  (if (empty? params)
    [[:admin.orders/set-default-route]]
    [[:admin.orders/fetch (db/parse-query-params params)]]))


(reg-event-fx
 :admin.orders/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   {:route (db/params->route (:params db))}))


(reg-event-fx
 :admin.orders/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:orders/query query-params]
    :db       (update db :params merge query-params)}))


(reg-event-fx
 :admin.orders.status/select
 [(path db/path)]
 (fn [{db :db} [_ status]]
   (let [statuses  (get-in db [:params :statuses])
         statuses' (cond
                     (= status :all)             #{:all}
                     (contains? statuses :all)   (conj (disj statuses :all) status)
                     (contains? statuses status) (disj statuses status)
                     :otherwise                  (conj statuses status))]
     {:route (db/params->route (assoc (:params db) :statuses (if (empty? statuses') #{:all} statuses')))})))


(reg-event-fx
 :admin.orders.range/change
 [(path db/path)]
 (fn [{db :db} [_ from to]]
   {:route (db/params->route (assoc (:params db) :from from :to to))}))


(reg-event-fx
 :admin.orders/datekey
 [(path db/path)]
 (fn [{db :db} [_ datekey]]
   {:route (db/params->route (assoc (:params db) :datekey datekey))}))
