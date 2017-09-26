(ns odin.global.subs
  (:require [odin.global.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::global
 (fn [db _]
   (db/path db)))


(reg-sub
 :global/messages
 :<- [::global]
 (fn [db _]
   (:messages db)))
