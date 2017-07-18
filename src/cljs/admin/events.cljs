(ns admin.events
  (:require [admin.db :as db]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))


(reg-event-db
 :app/init
 (fn [_ _]
   db/default-value))


(reg-event-db
 :menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))
