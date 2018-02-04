(ns admin.events
  (:require [admin.db :as db]
            [admin.accounts.events]
            [admin.kami.events]
            [admin.metrics.events]
            [admin.orders.events]
            [admin.properties.events]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))


(reg-event-fx
 :app/init
 (fn [_ [_ account]]
   {:db         (db/bootstrap account)
    :dispatch-n [[:history/bootstrap]]}))


(reg-event-db
 :layout.mobile-menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))
