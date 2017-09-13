(ns odin.orders.admin.events
  (:require [odin.orders.admin.db :as db]
            [odin.utils.norms :as norms]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin/orders [route]
  [[:admin.orders/fetch]])


(reg-event-fx
 :admin.orders/fetch
 [(path db/path)]
 (fn [{db :db} [k]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:orders {:params {}}
                 [:id :price :created :quantity :name :desc :status :billed_on
                  [:account [:id :name]]
                  [:service [:id :name :code :billed :price]]
                  [:property [:id :name]]
                  [:payments [:id :amount]]]]]
               :on-success [::fetch-orders k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-orders
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [records (get-in response [:data :orders])]
     {:db       (norms/normalize db :orders/norms records)
      :dispatch [:loading k false]})))
