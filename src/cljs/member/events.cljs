(ns member.events
  (:require [member.db :as db]
            [member.routes :as routes]
            [member.profile.events]
            [member.services.events]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))


(reg-event-fx
 :app/init
 (fn [_ [_ account]]
   {:db (db/bootstrap account)}))


(reg-event-db
 :layout.mobile-menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))


(reg-event-db
 :user/update
 (fn [db [_ data]]
   (update db :account merge data)))
