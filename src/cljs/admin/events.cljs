(ns admin.events
  (:require [admin.db :as db]
            [admin.routes :as routes]
            [admin.account.list.events]
            [admin.account.entry.events]
            [clojure.string :as string]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))

(reg-event-db
 :app/init
 (fn [_ _]
   db/default-value))


(reg-event-db
 :menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))


(defn- extract-root [page]
  (if-let [n (and page (namespace page))]
    (-> (string/split n #"\.") first keyword)
    page))


(reg-event-fx
 :route/change
 (fn [{:keys [db]} [_ page params]]
   (let [route {:root   (extract-root page)
                :page   page
                :params params}]
     {:db         (assoc db :route route)
      :dispatch-n (routes/dispatches route)})))
