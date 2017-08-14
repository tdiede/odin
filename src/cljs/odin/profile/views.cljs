(ns odin.profile.views
  (:require [odin.views.content :as content]
            [odin.profile.membership.views]
            [odin.profile.settings.views]
            [odin.routes :as routes]))


(defmethod content/view :profile [route])
