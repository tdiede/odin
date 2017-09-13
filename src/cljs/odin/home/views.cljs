(ns odin.home.views
  (:require [odin.content :as content]
            [odin.home.admin.views :as admin]
            [antizer.reagent :as ant]))


;; =============================================================================
;; Admin
;; =============================================================================


(defmethod content/view :admin/home [route]
  [ant/card {:title "Admin Home"}])


;; =============================================================================
;; Member
;; =============================================================================


(defn member [route]
  [ant/card {:title "This is member home."}])


(defmethod content/view :member/home [route]
  [member route])
