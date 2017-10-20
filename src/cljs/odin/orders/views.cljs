(ns odin.orders.views
  (:require [odin.content :as content]
            [odin.orders.admin.list.views :as admin-list]
            [odin.orders.admin.entry.views :as admin-entry]
            [antizer.reagent :as ant]))


;; =============================================================================
;; Admin
;; =============================================================================


(defmethod content/view :admin/orders [route]
  [admin-list/view])


(defmethod content/view :admin.orders/entry [route]
  [admin-entry/view route])
