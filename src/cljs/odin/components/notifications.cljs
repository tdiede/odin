(ns odin.components.notifications
  (:require [antizer.reagent :as ant]
            [re-frame.core :refer [reg-event-fx]]
            [reagent.core :as r]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(defn level->class-name
  "Acceptable inputs are :info, :warning, :success, and :danger."
  [level]
  (case level
    :danger  "is-danger"
    :warning "is-warning"
    :success "is-success"
    :info    "is-info"
    ""))

(defn banner
  "Renders a notification element on-screen, intended to communicate important messages."
  ([message]
   [banner message :nil true])
  ([message level]
   [banner message level true])
  ([message level is-cancelable]
   [:div.notification {:class (level->class-name level)}
    (if (= is-cancelable true) [:button.delete])
    message]))

(defn banner-danger [message]
  [banner message :danger false])

(defn banner-warning [message]
  [banner message :warning false])

(defn banner-info
  ([message]
   [banner-info message true])
  ([message cancelable]
   [banner message :info cancelable]))

(defn banner-success
  ([message]
   [banner-success message true])
  ([message cancelable]
   [banner message :success cancelable]))



(defn banner-global
  ([message]
   [:div.global-notification {:class (level->class-name :info)}
    message])
  ([message level]
   [:div.global-notification {:class (level->class-name level)}
    message]))


;; =============================================================================
;; Events
;; =============================================================================
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
