(ns member.services.member.events
  (:require [member.routes :as routes]
            [member.services.member.db :as db]
            [re-frame.core :refer [reg-event-fx reg-event-db path]]
            [toolbelt.core :as tb]))



(defmethod routes/dispatches :member.services/book [{:keys [params page] :as route}]
  (if (empty? params)
    [[:member.services/set-default-route route]]
    [[:member.services/fetch (db/parse-query-params page params)]]))


(reg-event-fx
 :member.services/set-default-route
 [(path db/path)]
 (fn [{db :db} [_ {page :page}]]
   {:route (db/params->route page (:params db))}))


(reg-event-fx
 :member.services/fetch
 [(path db/path)]
 (fn [{db :db} [_ query-params]]
   {:db (assoc db :params query-params)}))


(reg-event-fx
 :member.services.section/select
 [(path db/path)]
 (fn [_ [_ section]]
   (let [page (if (= section :book) :services/book :services/manage)]
     {:route (routes/path-for page)})))


(reg-event-db
 :member.services.add-service.form/update
 [(path db/path)]
 (fn [db [_ key value]]
   (assoc-in db [:form-data key] value)))


(reg-event-db
 :member.services.add-service.form/reset
 [(path db/path)]
 (fn [db _]
   (dissoc db :form-data)))


(reg-event-fx
 :member.services.add-service/show
 [(path db/path)]
 (fn [db _]
   {:dispatch [:modal/show db/modal]}))


(reg-event-fx
 :member.services.add-service/close
 [(path db/path)]
 (fn [{db :db} [_ modal]]
   {:dispatch-n [[:member.services.add-service.form/reset]
                 [:modal/hide db/modal]]}))


(reg-event-db
 :member.services.add-service/add
 [{path db/path}]
 (fn [db]
   (tb/log db)
   (assoc-in db [:cart] "test")))
