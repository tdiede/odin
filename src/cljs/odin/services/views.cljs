(ns odin.services.views
  (:require [odin.content :as content]
            [odin.services.member.views :as member]
            [toolbelt.core :as tb]))


(defmethod content/view :member/services [route]
  [member/view route])
