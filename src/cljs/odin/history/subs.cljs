(ns odin.history.subs
  (:require [odin.history.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::history
 (fn [db _]
   (db/path db)))


(reg-sub
 :history
 :<- [::history]
 (fn [db [_ entity-id]]
   (group-by :a (get db entity-id))))
