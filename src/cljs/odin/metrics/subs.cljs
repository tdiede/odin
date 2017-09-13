(ns odin.metrics.subs
  (:require [odin.metrics.db :as db]
            [re-frame.core :refer [reg-sub]]))


(reg-sub
 ::metrics
 (fn [db _]
   (db/path db)))


(reg-sub
 :metrics.category/current
 :<- [::metrics]
 (fn [db _]
   (:category db)))


(reg-sub
 :metrics/referrals
 :<- [::metrics]
 (fn [db _]
   (:referrals db)))
