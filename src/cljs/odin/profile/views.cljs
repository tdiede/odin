(ns odin.profile.views
  (:require [odin.content :as content]
            [odin.profile.membership.views]
            [odin.profile.payments.views]
            [odin.profile.settings.views]
            [odin.components.subnav :refer [subnav]]
            [odin.routes :as routes]))

(defn content []
  [:div.columns
   [:div.column.is-one-quarter
    [subnav [["Membership" :profile/membership]
             ["Payments"   :profile/payment-history]
             ["Settings"   :profile/settings]]]]
   [:div.column
    [:h1 "Profile"]]])

(defmethod content/view :profile [route]
  [content])
