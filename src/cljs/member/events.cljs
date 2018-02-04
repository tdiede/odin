(ns member.events
  (:require [member.db :as db]
            [member.routes :as routes]
            [iface.odin.routes :refer [split-keyword]]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))


(reg-event-fx
 :app/init
 (fn [_ [_ account]]
   {:db (db/bootstrap account)}))


(reg-event-db
 :layout.mobile-menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))
