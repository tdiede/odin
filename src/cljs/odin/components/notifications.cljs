(ns odin.components.notifications
  (:require [antizer.reagent :as ant]
            [odin.routes :as routes]))


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
   [banner message :info true])
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
