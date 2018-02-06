(ns member.services.views
  (:require [member.content :as content]
            [member.services.member.views :as member]
            [toolbelt.core :as tb]))


(defmethod content/view :services [route]
  [member/view route])
