(ns odin.home.views
  (:require [odin.content :as content]
            [odin.profile.views :as profile]
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


;; NOTE: Changed for purposes of initial release. Fix at future date.
(defmethod content/view :member/home [route]
  [profile/content route])
