(ns admin.services.subs
  (:require [admin.services.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :services/list
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :services/norms)))
