(ns odin.components.order
  (:require [toolbelt.core :as tb]))


(defn status-icon
  "antd icon names that represent the status of an order."
  [status]
  (get {:pending   "clock-circle-o"
        :placed    "sync"
        :fulfilled "check-circle-o"
        :failed    "exclamation-circle-o"
        :charged   "credit-card"
        :canceled  "close-circle-o"}
       status))
