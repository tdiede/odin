(ns odin.orders.db
  (:require [odin.orders.admin.list.db :as admin-list]))


(def path ::orders)


(def default-value
  (merge admin-list/default-value {path {}}))
