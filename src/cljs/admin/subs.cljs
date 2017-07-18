(ns admin.subs
  (:require [re-frame.core :refer [reg-sub]]))


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
