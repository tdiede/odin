(ns odin.accounts.admin.db
  (:require [odin.accounts.admin.list.db :as list-db]
            [odin.accounts.admin.entry.db :as entry-db]))


(def default-value
  (merge list-db/default-value entry-db/default-value))
