(ns odin.orders.db
  (:require [odin.orders.admin.db :as admin]))


(def default-value
  (merge admin/default-value {}))
