(ns admin.services.events
  (:require [admin.services.db :as db]
            [admin.services.orders.events]
            [admin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))

;; ====================================================
;; list
;; ====================================================

(reg-event-fx
 :services/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query      [[:services {:params {}}
                             [:id :name :code :billed :price :cost]]]
               :on-success [::services-query k params]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::services-query
 [(path db/path)]
 (fn [{db :db} [_ k params response]]
   {:db (->> (get-in response [:data :services])
             (norms/normalize db :services/norms))
    :dispatch [:ui/loading k false]}))


(defmethod routes/dispatches :services/list
  [route]
  [[:services/query]])


;; ====================================================
;; entry
;; ====================================================

(reg-event-fx
 :service/fetch
 [(path db/path)]
 (fn [{db :db} [k service-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query      [[:service {:id service-id}
                             [:id :name :desc :code :price :cost :billed :rental
                              [:variants [:id :name :cost :price]]]]
                            [:orders {:params {:services [service-id]}}
                             [:id]]]
               :on-success [::service-fetch-success k]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::service-fetch-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [service (get-in response [:data :service])
         order-count (count (get-in response [:data :orders]))]
     {:db (norms/assoc-norm db :services/norms (:id service) (assoc service :order-count order-count))
      :dispatch [:ui/loading k false]})))


(defmethod routes/dispatches :services/entry
  [route]
  [[:service/fetch (tb/str->int (get-in route [:params :service-id]))]])
