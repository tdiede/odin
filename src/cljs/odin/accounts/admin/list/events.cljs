(ns odin.accounts.admin.list.events
  (:require [odin.accounts.admin.list.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin.accounts/list [{params :params}]
  (if (empty? params)
    [[:admin.accounts.list/set-default-route]]
    [[:admin.accounts.list/fetch (db/parse-query-params params)]]))


(reg-event-fx
 :admin.accounts.list/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   {:route (db/params->route (:params db))}))


(defn- accounts-query-params [params]
  {:roles [(keyword (:selected-role params))]})


(reg-event-fx
 :admin.accounts.list/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:accounts/query (accounts-query-params query-params)]
    :db       (assoc db :params query-params)}))


(reg-event-fx
 :admin.accounts.list/select-role
 [(path db/path)]
 (fn [{db :db} [_ role]]
   {:route (-> (:params db)
               (assoc :selected-role role)
               (dissoc :sort-by :sort-order)
               (db/params->route))}))
