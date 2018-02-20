(ns admin.services.events
  (:require [admin.services.db :as db]
            [admin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))

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
