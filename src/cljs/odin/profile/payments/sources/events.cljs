(ns odin.profile.payments.sources.events
  (:require [odin.profile.payments.sources.db :as db]
            [odin.profile.paymens.sources.autopay :as autopay]
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
                [:id :last4 :customer :type :name :status :autopay
                 [:payments [:id :method :for :autopay :amount :status :pstart :pend :paid_on]]]]]
              :on-success [:payment.sources.fetch/success]
              :on-failure [:payment.sources.fetch/failure]}}))


(reg-event-fx
 :payment.sources.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   (let [payment-sources (get-in response [:data :payment_sources])
         route           (when (and (not (empty? payment-sources))
                                    (nil? (:current db)))
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
 :payment.sources.add.bank/update!
 [(path db/add-path)]
 (fn [db [_ k v]]
   (assoc-in db [:bank k] v)))


(reg-event-db
 :payment.sources.add.bank/submit!
 [(path db/add-path)]
 (fn [db [_]]
   (tb/log "Submitting:" (:bank db))
   db))


(reg-event-fx
 :payment.sources.add.bank/submit!
 [(path db/add-path)]
 (fn [{:keys [db]} [_]]
   (let [{:keys [account-holder account-number routing-number]} (:bank db)]
     {:dispatch [:loading :payment.sources.add/bank true]
      :stripe.bank-account/create-token
      {:country             "US"
       :currency            "USD"
       :account-holder-type "individual"
       :key                 (.-key js/stripe)
       :account-holder-name account-holder
       :routing-number      routing-number
       :account-number      account-number
       :on-success          [::create-bank-token-success]
       :on-failure          [::create-bank-token-failure]}})))


(reg-event-fx
 ::create-bank-token-failure
 [(path db/add-path)]
 (fn [{:keys [db]} [_ error]]
   (tb/error error)
   {:dispatch [:loading :payment.sources.add/bank false]}))


(reg-event-fx
 ::create-bank-token-success
 (fn [_ [_ {token :id :as result}]]
   ;;(tb/log result)
   {:graphql
    {:mutation   [[:add_payment_source {:token token} [:id]]]
     :on-success [::create-bank-source-success]
     :on-failure [::create-bank-source-fail]}}))


(reg-event-fx
 ::create-bank-source-success
 (fn [{:keys [db]} [_ response]]
   (let [account-id (get-in db [:config :account :id])]
     {:dispatch-n [[:loading :payment.sources.add/bank false]
                   [:modal/hide :payment.source/add]
                   [:payment.sources/fetch account-id]]
      :route      (routes/path-for :profile.payment/sources
                                   :query-params {:source-id (get-in response [:data :add_payment_source :id])})})))

(reg-event-fx
 ::create-bank-source-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :payment.sources.add/bank false]
                 [:graphql/notify-errors! response]]}))


;; =============================================================================
;; Misc
;; =============================================================================

(reg-event-fx
 :stripe/load-scripts
 (fn [_ [_ version]]
   {:load-scripts [(str "https://js.stripe.com/" (or version "v2") "/")]}))
