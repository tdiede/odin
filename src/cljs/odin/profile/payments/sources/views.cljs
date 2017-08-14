(ns odin.profile.payments.sources.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [odin.components.subnav :refer [subnav]]))


(defmethod content/view :profile/payment-sources [route]
  [:div.columns
   [:div.column.is-one-quarter
    [subnav [["History" :profile/payment-history]
             ["Sources" :profile/payment-sources]]]]
   [:div.column
    [:h1 "Payment Sources"]]])
