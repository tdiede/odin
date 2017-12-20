(ns odin.accounts.admin.db
  (:require [odin.accounts.admin.list.db :as list-db]))



(def default-value
  (merge list-db/default-value))
