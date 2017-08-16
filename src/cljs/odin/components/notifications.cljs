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

(defn page-notification
  "Renders a notification element on-screen, intended to communicate important messages."
  ([message]
   [page-notification message :info true])
  ([message level]
   [page-notification message level true])
  ([message level is-cancelable]
   [:div.notification {:class (level->class-name level)}
    (if (= is-cancelable true) [:button.delete])
    message
    ]))
