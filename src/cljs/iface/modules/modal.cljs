(ns iface.modules.modal
  (:require [re-frame.core :as rf :refer [reg-event-db
                                          reg-event-fx
                                          reg-sub]]))



;; =============================================================================
;; DB
;; =============================================================================


(def path ::modals)


(def default-value
  {path {:visible {}}})



;; =============================================================================
;; Events
;; =============================================================================


(reg-event-db
 :modal/toggle
 [(rf/path path)]
 (fn [db [_ k v]]
   (assoc-in db [:visible k] v)))


(reg-event-fx
 :modal/show
 [(rf/path path)]
 (fn [_ [_ k]]
   {:dispatch [:modal/toggle k true]}))


(reg-event-fx
 :modal/hide
 [(rf/path path)]
 (fn [_ [_ k]]
   {:dispatch [:modal/toggle k false]}))


;; =============================================================================
;; Subscriptions
;; =============================================================================


(reg-sub
 ::modals
 (fn [db _]
   (path db)))


(reg-sub
 :modal/visible?
 :<- [::modals]
 (fn [db [_ k]]
   (get-in db [:visible k])))
