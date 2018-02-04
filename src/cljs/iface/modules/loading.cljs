(ns iface.modules.loading
  (:require [re-frame.core :as rf]))


(def path
  ::loading)


(def db
  {path {}})


(defn install-module! []
  (rf/reg-sub
   path
   (fn [db _]
     (path db)))


  (rf/reg-sub
   :ui/loading?
   :<- [path]
   (fn [db [_ k]]
     (get db k)))


  (rf/reg-event-db
   :ui/loading
   [(rf/path path)]
   (fn [db [_ k v]]
     (assoc db k v))))
