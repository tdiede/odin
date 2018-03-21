(ns iface.modules.notification
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-fx
                                   reg-fx]]))


;; ==============================================================================
;; events =======================================================================
;; ==============================================================================

;; TODO - extend to include an optional params map
(reg-fx
 :notification
 (fn [[type message]]
   (case type
     :info    (ant/notification-info {:message message})
     :success (ant/notification-success {:message message})
     :error   (ant/notification-error {:message message})
     :warning (ant/notification-warning {:message message}))))

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
