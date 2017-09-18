(ns odin.profile.payments.sources.subs
  (:require [odin.profile.payments.sources.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 ::sources
 (fn [db _]
   (db/path db)))


;; =============================================================================
;; All Sources
;; =============================================================================


;; Payment Sources (Linked Accounts)
;; - Can optionally filter by source's :type
(reg-sub
 :payment/sources
 :<- [::sources]
 ;;(fn [db _]
 ;;  (:sources db)))
 (fn [db [_ type]]
   (if (nil? type)
     (:sources db)
     (filter #(= (:type %) type) (:sources db)))))


(reg-sub
 :payment.sources/loading?
 :<- [::sources]
 (fn [db _]
   (get-in db [:loading :list])))


;; =============================================================================
;; Nav
;; =============================================================================


;; Yields the ID of currently-selected Source from DB
(reg-sub
 :payment.sources/current-id
 :<- [::sources]
 (fn [db _]
   (:current db)))


;; Yields the Source that corresponds to currently-selected Source ID
(reg-sub
 :payment.sources/current
 :<- [:payment/sources]
 :<- [:payment.sources/current-id]
 (fn [[sources source-id] _]
   (tb/find-by (fn [source] (= source-id (:id source))) sources)))


;; =============================================================================
;; Add Source
;; =============================================================================


(reg-sub
 ::add-source
 (fn [db _]
   (db/add-path db)))


(reg-sub
 :payment.sources.add/available-types
 :<- [::add-source]
 (fn [db _]
   (:available-types db)))


;; Yields the ID of currently-selected Source from DB
(reg-sub
 :payment.sources.add/type
 :<- [::add-source]
 (fn [db _]
   (:type db)))


;; =============================================================================
;; Autopay
;; =============================================================================


;; (reg-sub
;;  :payment.sources/autopay-source
;;  :<- [:payment/sources]
;;  (fn [[sources] _]
;;    (filter #(= (get % :autopay) true) sources)))


;; =============================================================================
;; Default Source
;; =============================================================================
