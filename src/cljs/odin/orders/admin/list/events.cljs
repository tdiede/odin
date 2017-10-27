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
    :db       (assoc db :params query-params)}))


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


(reg-event-fx
 :admin.orders/search-members
 [(path db/path)]
 (fn [{db :db} [k query]]
   {:dispatch-throttle {:id              k
                        :window-duration 500
                        :leading?        false
                        :trailing?       true
                        :dispatch        [::search-members k query]}}))


(reg-event-fx
 ::search-members
 [(path db/path)]
 (fn [{db :db} [_ k query]]
   {:dispatch [:loading k true]
    :graphql  {:query      [[:accounts {:params {:roles [:member]
                                                 :q     query}}
                             [:id :name :email]]]
               :on-success [::search-members-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::search-members-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db      (assoc db :accounts (get-in response [:data :accounts]))
    :dispatch [:loading k false]}))


(reg-event-fx
 :admin.orders/select-members
 [(path db/path)]
 (fn [{db :db} [_ selected]]
   {:db    (assoc db :selected-accounts selected)
    :route (db/params->route (assoc (:params db) :accounts (map (comp tb/str->int :key) selected)))}))



(reg-event-fx
 :admin.orders.filters/reset
 [(path db/path)]
 (fn [{db :db} _]
   {:db    (dissoc db :selected-accounts)
    :route (db/params->route db/default-params)}))
