(ns odin.accounts.db
  (:require [odin.accounts.admin.db :as admin]))


(def path ::accounts)


(def default-value
  (merge admin/default-value {path {}}))
