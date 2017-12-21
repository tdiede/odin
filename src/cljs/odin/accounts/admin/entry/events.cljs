(ns odin.accounts.admin.entry.events
  (:require [odin.accounts.admin.entry.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin.accounts/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [[:account/fetch account-id] [:payments/fetch account-id]]))
