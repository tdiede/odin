(ns odin.accounts.admin.views
  (:require [odin.accounts.admin.entry.views :as entry]
            [odin.accounts.admin.list.views :as list]
            [odin.content :as content]))

(defmethod content/view :admin.accounts/list [route]
  [list/view])


(defmethod content/view :admin.accounts/entry [route]
  [entry/view route])
