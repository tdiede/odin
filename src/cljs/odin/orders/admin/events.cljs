(ns odin.orders.admin.events
  (:require [odin.orders.admin.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defn- transform-params [new-params]
  (let [{:keys [date-range] :as params} (js->clj new-params :keywordize-keys true)]
    (merge params {:from (first date-range) :to (second date-range)})))


(reg-event-fx
 :orders.admin.chart/params
 [(path db/path)]
 (fn [{db :db} [_ new-params]]
   (let [new-params (transform-params new-params)]
     (tb/log new-params)
     {:db (update-in db [:chart :params] merge new-params)})))
