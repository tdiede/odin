(ns odin.profile.payments.sources.autopay
  (:require [odin.profile.payments.sources.db :as db]
            [odin.components.modals]
            [odin.routes :as routes]
            [reagent.ratom :as r :refer-macros [reaction]]
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


(defn get-autopay-source
  [sources]
  (first (filter #(true? (:autopay %)) sources)))

(defn has-autopay-source
  [sources]
  (let [has (empty? (filter #(true? (:autopay %)) sources))]
    ;;(println "has autopay? " has (filter #(true? (:autopay %)) sources))
    has))



;; Returns currently selected Autopay source, if it exists.
(reg-sub
 :payment.sources/autopay-source
 :<- [::sources]
 (fn [db _]
   (get-in db [:autopay :source])))
   ;;(get-autopay-source (:sources db))))
   ;;(reaction (first (filter #(true? (:autopay %)) (:sources db))))))


(reg-sub
 :payment.sources/autopay-on?
 :<- [::sources]
 (fn [db _]
   (get-in db [:autopay :on])))
   ;;(not (empty? (filter #(true? (:autopay %)) (:sources db))))))


; (tb/log get-autopay-source)
; (initialize)

(reg-event-fx
 :payment.sources.autopay/confirm-modal
 [(path db/path)]
 (fn [_ [_ enabling]]
   (if (= true enabling)
     {:dispatch [:payment.sources.autopay/enable!]}
     {:dispatch [:modal/show :payment.source/autopay-disable]})))


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

(defn- get-default-source [sources]
  (first (filter #(= (:default %) true) sources)))

(reg-event-fx
 :payment.sources.autopay/enable!
 [(path db/path)]
 (fn [{:keys [db]} _]
   (let [source (get-default-source (:sources db))
         id     (:id source)]
     {:db      (assoc-in db [:loading :list] true)
      :graphql {:mutation   [[:set_autopay_source {:id id} [:id]]]
                :on-success [:payment.sources/fetch]
                :on-failure [:payment.sources/fetch]}})))


;;[:notify/success "Great! Autopay is now enabled."]

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


(reg-event-db
 :payment.sources.autopay/toggle!
 [(path db/path)]
 (fn [db [_ value]]
    ;;(let [sources (subscribe [:payment.sources/autopay-sources])
          ;;active  (subscribe [:payment.sources/autopay-source])]
   ;;(if (true? value))


   db))
      ;;(if (true? value)
        ;;(map disable-autopay sources))))
        ;;(map enable-autopay sources))))
