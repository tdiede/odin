(ns iface.modules.notification
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-fx]]))


;; ==============================================================================
;; events =======================================================================
;; ==============================================================================


;; TODO: Make fx!


(reg-event-fx
 :notify/success
 (fn [_ [_ message]]
   (ant/notification-info {:message  message
                           :icon     (r/as-element [ant/icon {:type "check" :style {:color "#11c956"}}])
                           :duration 6})))

(reg-event-fx
 :notify/failure
 (fn [_ [_ message]]
   (ant/notification-error {:message  message
                            :duration 6})))
