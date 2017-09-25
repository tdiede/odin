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


(reg-sub
 :payment/sources
 :<- [::sources]
 (fn [db [_ type]]
   (if (nil? type)
     (:sources db)
     (filter #(= (:type %) type) (:sources db)))))


(reg-sub
 :payment.sources/default-source
 :<- [::sources]
 (fn [db _]
   (tb/find-by :default (:sources db))))


(reg-sub
 :payment.sources/verified-banks
 :<- [:payment/sources :bank]
 (fn [banks _]
   (tb/log banks)
   (filter #(= (:status %) "verified") banks)))


;; =============================================================================
;; Current Sources
;; =============================================================================


(reg-sub
 :payment.sources/current-id
 :<- [::sources]
 (fn [db _]
   (:current db)))


(reg-sub
 :payment.sources/current
 :<- [:payment/sources]
 :<- [:payment.sources/current-id]
 (fn [[sources source-id] _]
   (tb/find-by (fn [source] (= source-id (:id source))) sources)))


(reg-sub
 :payment.sources.current/can-remove?
 :<- [:payment/sources :bank]
 :<- [:payment.sources/current]
 (fn [[banks current] _]
   (if (= :bank (:type current))
     (< 1 (count banks))
     true)))


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


(reg-sub
 :payment.sources.add/type
 :<- [::add-source]
 (fn [db _]
   (:type db)))


(reg-sub
 :payment.sources.bank.verify/microdeposits
 :<- [::add-source]
 (fn [db _]
   (:microdeposits db)))


;; =============================================================================
;; Autopay
;; =============================================================================


(reg-sub
 :payment.sources/can-enable-autopay?
 :<- [:payment/sources :bank]
 (fn [banks _]
   (some? (tb/find-by (comp #{"verified"} :status) banks))))


(reg-sub
 :payment.sources/autopay-source
 :<- [:payment/sources]
 (fn [sources _]
   (tb/find-by :autopay sources)))


(reg-sub
 :payment.sources/autopay-on?
 :<- [:payment.sources/autopay-source]
 (fn [source _]
   (:autopay source)))


;; =============================================================================
;; Default Source
;; =============================================================================
