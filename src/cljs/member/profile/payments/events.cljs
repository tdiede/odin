(ns member.profile.payments.events
  (:require [member.profile.payments.sources.events]
            [member.routes :as routes]))

(defmethod routes/dispatches :profile.payment/history [route]
  [[:payments/fetch (get-in route [:requester :id])]])
