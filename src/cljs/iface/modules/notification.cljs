(ns iface.modules.notification
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-fx
                                   reg-fx]]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; events =======================================================================
;; ==============================================================================

;; TODO - extend to include an optional params map
(reg-fx
 :notification
 (fn [[type message {:keys [description duration]}]]
   (let [values (tb/assoc-when
                 {:message message}
                 :description description
                 :duration duration)]
    (case type
      :info    (ant/notification-info values)
      :success (ant/notification-success values)
      :error   (ant/notification-error values)
      :warning (ant/notification-warning values)))))


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
