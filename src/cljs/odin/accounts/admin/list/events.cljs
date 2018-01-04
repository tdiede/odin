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


(defn- accounts-query-params [{:keys [selected-view q] :as query-params}]
  (let [roles (when-not (= "all" selected-view)
                [(keyword selected-view)])]
    (tb/assoc-when {:q q} :roles roles)))


(reg-event-fx
 :admin.accounts.list/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:accounts/query (accounts-query-params query-params)]
    :db       (assoc db :params query-params)}))


(reg-event-fx
 :admin.accounts.list/select-view
 [(path db/path)]
 (fn [{db :db} [_ view]]
   {:route (-> (:params db)
               (assoc :selected-view view)
               (merge (db/default-sort-params view))
               (db/params->route))}))


(reg-event-fx
 :admin.accounts.list/search-accounts
 [(path db/path)]
 (fn [{db :db} [k q]]
   {:dispatch-throttle {:id              k
                        :window-duration 500
                        :leading?        false
                        :trailing?       true
                        :dispatch        [::search-accounts q]}}))


(reg-event-fx
 ::search-accounts
 [(path db/path)]
 (fn [{db :db} [_ query]]
   {:route (db/params->route (assoc (:params db) :q query))}))
