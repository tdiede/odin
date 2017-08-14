(ns odin.profile.membership.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]))


(defmethod content/view :profile/membership [route]
  [:h1 "Membership"])
