(ns odin.services.views
  (:require [odin.content :as content]
            [odin.services.member.views :as member]))


(defmethod odin.content/view :member/services [route]
  [member/view route])
