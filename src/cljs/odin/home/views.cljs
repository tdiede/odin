(ns odin.home.views
  (:require [odin.content :as content]
            [antizer.reagent :as ant]))


;; =============================================================================
;; Admin
;; =============================================================================


(defn admin [route]
  [ant/card {:title "This is admin home."}])


(defmethod content/view :admin/home [route]
  [admin route])


;; =============================================================================
;; Member
;; =============================================================================


(defn member [route]
  [ant/card {:title "This is member home."}])


(defmethod content/view :member/home [route]
  [member route])
