(ns odin.accounts.views
  (:require [odin.content :as content]
            [odin.accounts.admin.list.views :as admin-list]))


;; admin ========================================================================


(defmethod content/view :admin.accounts/list [route]
  [admin-list/view])


(defmethod content/view :admin.accounts/entry [route]
  [:h1 "Hello, account detail!"])
