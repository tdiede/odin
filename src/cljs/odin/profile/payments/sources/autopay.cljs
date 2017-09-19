(ns odin.profile.payments.sources.autopay
  (:require [odin.profile.payments.sources.db :as db]
            [odin.components.modals]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-sub
                                   subscribe
                                   reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))

(reg-sub
 ::sources
 (fn [db _]
   (db/path db)))


;; Returns currently selected Autopay source, if it exists.
(reg-sub
 :payment.sources/autopay-source
 :<- [::sources]
 (fn [db _]
   (first (filter #(true? (:autopay %)) (:sources db)))))


(reg-sub
 :payment.sources/autopay-on?
 :<- [::sources]
 (fn [db _]
   (not (empty? (filter #(true? (:autopay %)) (:sources db))))))


; (tb/log get-autopay-source)
; (initialize)

(reg-event-fx
 :payment.sources.autopay/confirm-modal
 [(path db/path)]
 (fn [_ [_ enabling]]
   ;;(tb/log enabling)
   (if (= true enabling)
     {:dispatch [:modal/show :payment.source/autopay-enable]}
     {:dispatch [:modal/show :payment.source/autopay-disable]})))


;;(reg-event-fx
;; :payment.sources.autopay/confirm-enable
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   {:dispatch [:modal/show :payment.source/autopay-enable]}))
;;
;;
;;(reg-event-fx
;; :payment.sources.autopay/confirm-disable
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   {:dispatch [:modal/show :payment.source/autopay-disable]}))

;;(reg-event-db
;; :payment.sources.autopay/enable!
;; [(path db/path)]
;; (fn [db _]
;;   (let [sources (subscribe [:payment.sources/autopay-sources])]
;;     (assoc (first @sources) :autopay true))))


;;(reg-event-fx
;; :payment.sources.autopay/disable!
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   (let [id (:id @(subscribe [:payment.sources/autopay-source]))]
;;     (tb/log id)
;;     {:db      (assoc-in db [:loading :list] true)
;;      :graphql {:mutation   [[:unset_autopay_source {:id id} [:id]]]
;;                :on-success [:payment.sources/fetch]
;;                :on-failure [:payment.sources/fetch]}})))

(reg-event-fx
 :payment.sources.autopay/enable!
 [(path db/path) debug]
 (fn [{:keys [db]} [_ source-id]]
   ;;(let [source "ba_19Z7BcJDow24Tc1aZBrHmWB5"]
     ;;(tb/log "enabling " source-id " for autopay")
     {:db      (assoc-in db [:loading :list] true)
      :graphql {:mutation   [[:set_autopay_source {:id source-id} [:id]]]
                :on-success [:payment.sources/fetch]
                :on-failure [:payment.sources/fetch]}}))

     ;;(assoc active :autopay false))))

;;(reg-event-fx
;; :account/change-random-phone!
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   (let [account (rand-nth (get-in db [:accounts :list]))]
;;     {:db      (assoc-in db [:loading :accounts/list] true)
;;      :graphql {:mutation   [[:set_phone {:id    (:id account)
;;                                          :phone (rand-phone)}
;;                              [:id :phone]]]
;;                :on-success [:account.change-random-number/success]}})))

;;unset_autopay_source


;;(reg-event-db
;; :payment.sources.autopay/toggle!
;; [(path db/path)]
;; (fn [db [_ value]]
;;    (let [sources (subscribe [:payment.sources/autopay-sources])
;;          active  (subscribe [:payment.sources/autopay-source])]
;;        (tb/log @sources)
;;      ;;(if (true? value)
;;        (map disable-autopay sources))))
;;        ;;(map enable-autopay sources))))
