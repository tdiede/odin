(ns odin.profile.payments.views
  (:require [odin.views.content :as content]
            [odin.profile.payments.history.views]
            [odin.profile.payments.sources.views]
            [odin.components.subnav :refer [subnav]]))


(defmethod content/view :profile/payments [route]
  [:div.columns
   [:div.column.is-one-quarter]
    ; [subnav [["History" :profile/payment-history]
    ;          ["Sources" :profile/payment-sources]]]]
   [:div.column
    [:h1 "Payments"]]])
