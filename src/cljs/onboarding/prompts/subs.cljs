(ns onboarding.prompts.subs
  (:require [onboarding.db :as db]
            [re-frame.core :refer [reg-sub]]
            [clojure.string :as string]))

;; =============================================================================
;; Global
;; =============================================================================

(def ^:private titles
  {:overview/start
   "Welcome to the Starcity community!"

   :admin/emergency
   "Who should we contact in the event of an emergency?"

   :deposit/method
   "How would you like to pay your security deposit?"

   :deposit.method/bank
   "Please enter your bank account information."

   :deposit.method/verify
   "Please confirm the two amounts that have been deposited."

   :deposit/pay
   "We provide two options for paying your deposit."

   :services/moving
   "Need some help moving your belongings in?"

   :services/storage
   "Do you have belongings that you don't use often but can't part with?"

   :services/cleaning
   "No time for chores? Let us take care of the dirty work."

   :services/upgrades
   "Something else you need? We can add it on."

   :finish/review
   "You're almost a Starcity member!"})

(reg-sub
 :prompt/title
 :<- [:menu/active]
 (fn [active _]
   (get titles active (str "TODO: Implement title for " active))))

(reg-sub
 :prompt/active
 :<- [:db]
 :<- [:menu/active]
 (fn [[db active] _]
   (assoc (get db active) :keypath active)))

(reg-sub
 :prompt/complete?
 :<- [:prompt/active]
 (fn [prompt _]
   (get prompt :complete)))

(reg-sub
 :prompt/saving?
 (fn [db _]
   (:saving db)))

(reg-sub
 :prompt/dirty?
 :<- [:prompt/active]
 (fn [prompt _]
   (:dirty prompt)))

(reg-sub
 :prompt/can-save?
 :<- [:prompt/active]
 (fn [prompt _]
   (db/can-save? prompt (:keypath prompt))))

;; =============================================================================
;; Navigation
;; =============================================================================

;; =============================================================================
;; Advance

(reg-sub
 :prompt/can-advance?
 :<- [:prompt/active]
 (fn [prompt _]
   (db/can-advance? prompt)))

;; =============================================================================
;; Retreat

(reg-sub
 :prompt/has-previous?
 :<- [:db]
 :<- [:menu/active]
 (fn [[db keypath] _]
   (not (nil? (db/previous-prompt db keypath)))))

;; =============================================================================
;; Prompt-specific
;; =============================================================================

;; =============================================================================
;; Security Deposit

(reg-sub
 :deposit/payment-method
 (fn [db _]
   (get-in db [:deposit/method :data :method])))

(reg-sub
 :deposit.pay/amount
 (fn [db _]
   (aget js/account "full-deposit")))

(reg-sub
 :deposit.pay/llc
 (fn [db _]
   (aget js/account "llc")))

;; =============================================================================
;; Orders

(reg-sub
 :orders
 (fn [db _]
   (:orders/list db)))

(reg-sub
 :orders/loading?
 (fn [db _]
   (boolean (:orders/loading db))))

(reg-sub
 :orders/error?
 (fn [db _]
   (:orders.fetch/error db)))

;; =============================================================================
;; Review

(reg-sub
 :finish.review.cc/showing?
 (fn [db _]
   (:finish.review.cc-modal/showing db)))

(reg-sub
 :finish.review/finishing?
 (fn [db _]
   (:finishing db)))
