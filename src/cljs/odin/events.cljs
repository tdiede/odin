(ns odin.events
  (:require [odin.db :as db]
            [odin.routes :as routes]
            [odin.account.list.events]
            [odin.account.entry.events]
            [clojure.string :as string]
            [re-frame.core :refer [reg-event-db reg-event-fx]]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]))


(reg-event-db
 :app/init
 (fn [_ [_ config]]
   (assoc db/default-value :config config)))


(reg-event-db
 :app/init-error
 (fn [_ _]
   {:configure/error true}))


(reg-event-db
 :menu/toggle
 (fn [db _]
   (update-in db [:menu :showing] not)))


(defn- page-root [page]
  (if-let [n (and page (namespace page))]
    (-> (string/split n #"\.") first keyword)
    page))


(defn- page->path [page]
  (if-let [p (and page (namespace page))]
    (conj (->> (string/split p #"\.") (map keyword) vec) (keyword (name page)))
    [page]))


(reg-event-fx
 :route/change
 (fn [{:keys [db]} [_ page params]]
   (let [route {:root   (page-root page)
                :page   page
                :path   (page->path page)
                :params params}]
     {:db         (assoc db :route route)
      :dispatch-n (routes/dispatches route)})))
