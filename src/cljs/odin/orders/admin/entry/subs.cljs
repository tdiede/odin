(ns odin.orders.admin.entry.subs
  (:require [odin.orders.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 :admin.order/editing?
 :<- [db/path]
 (fn [db [_ order-id]]
   (get-in db [:admin.order/editing order-id])))
