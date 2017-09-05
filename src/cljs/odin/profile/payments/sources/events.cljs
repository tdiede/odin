(ns odin.profile.payments.sources.events
  (:require [odin.profile.payments.sources.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Routing/Nav
;; =============================================================================


(defmethod routes/dispatches :profile.payment/sources [route]
  [[:payment.sources/fetch (get-in route [:requester :id])]
   [:payment.sources/set-current (get-in route [:params :source-id])]])


(reg-event-db
 :payment.sources/set-current
 [(path db/path)]
 (fn [db [_ current-source-id]]
   (assoc-in db [:current] current-source-id)))


;; =============================================================================
;; Fetch Sources
;; =============================================================================


(reg-event-fx
 :payment.sources/fetch
 [(path db/path)]
 (fn [{:keys [db]} [_ account-id]]
   {:db      (assoc-in db [:loading :list] true)
    :graphql {:query
              [[:payment_sources {:account account-id}
                [:id :last4 :customer :type :name :status
                 [:payments [:id :method :for :autopay :amount :status :pstart :pend :paid_on]]]]]
              :on-success [:payment.sources.fetch/success]
              :on-failure [:payment.sources.fetch/failure]}}))


(reg-event-fx
 :payment.sources.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   (let [payment-sources (get-in response [:data :payment_sources])
         route           (when (nil? (:current db))
                           (routes/path-for :profile.payment/sources
                                            :query-params {:source-id (:id (first payment-sources))}))]
     (tb/assoc-when
      {:db (-> (assoc db :sources payment-sources)
               (assoc-in [:loading :list] false))}
      :route route))))


(reg-event-fx
 :payment.sources.fetch/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :list] false)
    :dispatch [:graphql/notify-errors! response]}))


;; =============================================================================
;; Add Source
;; =============================================================================


(reg-event-db
 :payment.sources.add/select-type
 [(path db/add-path)]
 (fn [db [_ type]]
   (assoc db :type type)))


(reg-event-db
 :payment.sources.add-new-account/update-bank
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:new-account-info-bank k] v)))


(reg-event-db
 :payment.sources.add-new-account/submit-bank!
 [(path db/path)]
 (fn [db [_]]
   (tb/log "Submitting:" (:new-account-info-bank db))
   db))


(reg-event-db
 :payment.sources.add-new-account/update-card
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:new-account-info-card k] v)))


(reg-event-db
 :payment.sources.add-new-account/submit-card!
 [(path db/path)]
 (fn [db [_]]
   (tb/log "Submitting:" (:new-account-info-card db))
   db))
