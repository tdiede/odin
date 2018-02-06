(ns admin.profile.payments.events
  (:require [admin.profile.payments.sources.events]
            [admin.routes :as routes]))

(defmethod routes/dispatches :profile.payment/history [route]
  [[:payments/fetch (get-in route [:requester :id])]])
