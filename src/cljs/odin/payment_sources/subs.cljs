(ns odin.payment-sources.subs
  (:require [odin.payment-sources.db :as db]
            [re-frame.core :refer [reg-sub subscribe]]
            [odin.utils.norms :as norms]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :payment-sources
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :payment-sources/norms)))


(reg-sub
 :payment-sources/by-type
 :<- [:payment-sources]
 (fn [sources [_ type]]
   (filter #(= (:type %) type) sources)))


(reg-sub
 :payment-sources/by-account-id
 :<- [:payment-sources]
 (fn [sources [_ account-id type]]
   (let [pred (if (some? type)
                #(and (= account-id (get-in % [:account :id]))
                      (= type (:type %)) sources)
                #(= account-id (get-in % [:account :id])))]
     (filter pred sources))))


(reg-sub
 :payment-sources/autopay-on?
 (fn [[_ account-id]]
   (subscribe [:payment-sources/by-account-id account-id]))
 (fn [sources _]
   (boolean (tb/find-by :autopay sources))))


(reg-sub
 :payment-sources/has-verified-bank?
 (fn [[_ account-id]]
   (subscribe [:payment-sources/by-account-id account-id :bank]))
 (fn [banks _]
   (not (empty? (filter #(= (:status %) "verified") banks)))))


(reg-sub
 :payment-sources/has-card?
 (fn [[_ account-id]]
   (subscribe [:payment-sources/by-account-id account-id :card]))
 (fn [cards _]
   (not (empty? cards))))
