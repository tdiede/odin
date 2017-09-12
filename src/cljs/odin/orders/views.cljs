(ns odin.orders.views
  (:require [odin.content :as content]
            [odin.orders.admin.views :as admin]
            [antizer.reagent :as ant]))


;; =============================================================================
;; Admin
;; =============================================================================


(defmethod content/view :admin/orders [route]
  [admin/view])
