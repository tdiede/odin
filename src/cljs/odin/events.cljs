(ns odin.events
  (:require [odin.db :as db]
            [odin.routes :as routes]
            [odin.global.events]
            [odin.accounts.events]
            [odin.history.events]
            [odin.kami.events]
            [odin.metrics.events]
            [odin.orders.events]
            [odin.payments.events]
            [odin.payment-sources.events]
            [odin.profile.events]
            [odin.properties.events]
            [odin.services.events]
            [clojure.string :as string]
            [re-frame.core :refer [reg-event-db reg-event-fx]]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]))


(reg-event-fx
 :app/init
 (fn [_ [_ config]]
   {:db       (db/configure config)
    :dispatch [:global/init config]}))


(reg-event-db
 :app/init-error
 (fn [_ _]
   {:configure/error true}))


(reg-event-db
 :menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))


(defn- page->path [page]
  (if-let [p (and page (namespace page))]
    (conj (->> (string/split p #"\.") (map keyword) vec) (keyword (name page)))
    [page]))


(reg-event-db
 :loading
 (fn [db [_ k v]]
   (assoc-in db [:loading k] v)))


(reg-event-fx
 :route/change
 (fn [{:keys [db]} [_ page params]]
   (let [route (merge
                (:route db)
                {:page   page
                 :path   (page->path page)
                 :params params})]
     {:db         (assoc db :route route)
      :dispatch-n (routes/dispatches route)})))


(reg-event-db
 :account/update
 (fn [db [_ account-data]]
   (update-in db [:config :account] merge account-data)))
