(ns odin.subs
  (:require [odin.account.list.subs]
            [odin.account.entry.subs]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 :config/error?
 (fn [db _]
   (:configure/error db)))


(reg-sub
 :menu/showing?
 (fn [db _]
   (get-in db [:menu :showing])))


(reg-sub
 :menu/items
 (fn [db _]
   (get-in db [:menu :items])))


(reg-sub
 :route/current
 (fn [db _]
   (:route db)))


;;; Config


(reg-sub
 ::config
 (fn [db _]
   (get db :config)))


(reg-sub
 :config/role
 :<- [::config]
 (fn [config _]
   (:role config)))


(reg-sub
 :config/features
 :<- [::config]
 (fn [config _]
   (-> config :features keys set)))
