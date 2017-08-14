(ns odin.profile.membership.views
  (:require [odin.content :as content]
            [odin.routes :as routes]))


(defmethod content/view :profile/membership [route]
  [:h1 "Membership"])
