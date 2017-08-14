(ns odin.profile.settings.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]))


(defmethod content/view :profile/settings [route]
  [:h1 "hello"])
