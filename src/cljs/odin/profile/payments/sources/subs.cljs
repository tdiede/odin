(ns odin.profile.payments.sources.subs
  (:require [odin.profile.payments.sources.db :as db]
            [re-frame.core :as rf :refer [reg-sub]]
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
 :<- [:auth]
 :<- [:payment-sources]
 (fn [[auth sources] [_ type]]
   (let [sources (filter #(= (:id auth) (get-in % [:account :id])) sources)]
     (if (some? type)
       (filter #(= (:type %) type) sources)
       sources))))


(reg-sub
 :payment.sources/service-source
 :<- [:payment/sources]
 (fn [sources _]
   (tb/find-by #(and (:default %) (= :card (:type %))) sources)))


(reg-sub
 :payment.sources/verified-banks
 :<- [:payment/sources :bank]
 (fn [banks _]
   (filter #(= (:status %) "verified") banks)))


(reg-sub
 :payment.sources/has-verified-bank?
 :<- [:payment.sources/verified-banks]
 (fn [banks _]
   (not (empty? banks))))


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
 :<- [:payment.sources/has-verified-bank?]
 :<- [:member.rent/unpaid?]
 (fn [[verified unpaid] _]
   (and verified (not unpaid))))


(reg-sub
 :payment.sources/autopay-source
 :<- [:payment/sources]
 (fn [sources _]
   (tb/log sources)
   (tb/find-by :autopay sources)))


(reg-sub
 :payment.sources/autopay-on?
 :<- [:payment.sources/autopay-source]
 (fn [source _]
   (:autopay source)))
