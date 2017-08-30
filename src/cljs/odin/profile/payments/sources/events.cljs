(ns odin.profile.payments.sources.events
  (:require [ajax.core :as ajax]
            [odin.profile.payments.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))


(reg-event-fx
 :payment.sources/set-current
 [(path db/path)]
 (fn [{:keys [db]} [_ current-source-id]]
   {:db (assoc-in db [:current-source] current-source-id)}))


(reg-event-db
 :payment.sources.add-new-account/select-type
 [(path db/path)]
 (fn [db [_ account-type]]
   (assoc db :new-account-type account-type)))

(reg-event-db
 :payment.sources.add-new-account/update-bank
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:new-account-info-bank k] v)))

(reg-event-db
 :payment.sources.add-new-account/submit-bank!
 [(path db/path)]
 (fn [db [_]]
   (tb/log "Submitting:" (:new-account-info-bank db))))

(reg-event-db
 :payment.sources.add-new-account/update-card
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:new-account-info-card k] v)))

(reg-event-db
 :payment.sources.add-new-account/submit-card!
 [(path db/path)]
 (fn [db [_]]
   (tb/log "Submitting:" (:new-account-info-card db))))

;;(reg-event-fx
;; :payment.sources.source/change-autopay
;; [(path db/path)]
;; (fn [{:keys [db]} [_ source value]]
;;   {:db (assoc-in db [:current-source] current-source-id)}))
