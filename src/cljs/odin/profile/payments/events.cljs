(ns odin.profile.payments.events
  (:require [odin.routes :as routes]
            [odin.profile.payments.sources.events]))

(defmethod routes/dispatches :profile.payment/history [route]
  [[:payments/fetch (get-in route [:requester :id])]])
