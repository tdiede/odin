(ns iface.components.notifications
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   reg-sub
                                   subscribe
                                   dispatch
                                   path]]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; util =========================================================================
;; ==============================================================================


(defn create
  ([key text route]
   (create text route :info))
  ([key text route level]
   {:key   key
    :text  text
    :route route
    :level level}))


;; ==============================================================================
;; views ========================================================================
;; ==============================================================================


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


(defn banner-icon
  [level]
  (case level
    :warning [ant/icon {:type "exclamation-circle-o"}]
    :danger [ant/icon {:type "exclamation-circle-o"}]
    [:span]))


(defn banner-global
  ([message]
   [banner-global message :info])
  ([message level]
   [:div.global-notification {:class (level->class-name level)}
    [banner-icon level]
    [:span message]])
  ([message level uri]
   [:a.global-notification {:class (level->class-name level)
                            :href  uri}
    [banner-icon level]
    [:span message]]))


(defn messages
  [messages]
  (let [messages (subscribe [::all])]
    [:div
     (for [{:keys [text route level]} @messages]
       ^{:key text} [banner-global text level route])]))


;; ==============================================================================
;; subs =========================================================================
;; ==============================================================================


(reg-sub
 ::root
 (fn [db _]
   (::root db)))


(reg-sub
 ::all
 :<- [::root]
 (fn [db _]
   (:messages db)))


;; ==============================================================================
;; events =======================================================================
;; ==============================================================================


(reg-event-db
 ::create
 [(path ::root)]
 (fn [db [_ msgs]]
   (update db :messages (if (map? msgs) conj concat) msgs)))


(reg-event-db
 ::clear
 [(path ::root)]
 (fn [db [_ key]]
   (if (some? key)
     (update db :messages (fn [ms] (filter #(not= (:key %) key) ms)))
     (assoc db :messages '()))))
