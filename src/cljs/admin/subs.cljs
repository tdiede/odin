(ns admin.subs
  (:require [re-frame.core :refer [reg-sub]]))


(reg-sub
 :menu/showing?
 (fn [db _]
   (get-in db [:menu :showing])))
