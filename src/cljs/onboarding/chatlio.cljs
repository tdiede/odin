(ns onboarding.chatlio
  (:require [re-frame.core :refer [reg-fx dispatch]]))


(defn- initialized? []
  (and (aget "_chatlio" js/window) (.-version js/window._chatlio)))

;; Ensures that the `on-ready` event is always fired at least once by checking
;; if the widget is already initialized by the time this code is
;; executed (presence of _chatlio.version).
(reg-fx
 :chatlio/ready
 (fn [on-ready]
   (if (initialized?)
     (dispatch on-ready)
     (.addEventListener js/document "chatlio.ready"
                        (fn [e]
                          (dispatch (conj on-ready e)))))))


(reg-fx
 :chatlio/show
 (fn [expanded]
   (.show js/window._chatlio #js {:expanded (or expanded false)})))


(reg-fx
 :chatlio/identify
 (fn [[email data]]
   (.identify js/window._chatlio email (clj->js (assoc data :email email)))))
