(ns odin.profile.payments.sources.events
  (:require [odin.profile.payments.sources.db :as db]
            [odin.profile.payments.sources.autopay :as autopay]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   subscribe
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Routing/Nav
;; =============================================================================


(defmethod routes/dispatches :profile.payment/sources [route]
  [[:payment.sources/fetch (get-in route [:requester :id])]
   ;;[:payment.source.autopay/fetch] ;;(get-in route [:requester :id])]
   [:payment.sources/set-current (get-in route [:params :source-id])]])


(reg-event-db
 :payment.sources/set-current
 [(path db/path)]
 (fn [db [_ current-source-id route]]
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
                [:id :last4 :customer :type :name :status :default ;;:autopay
                 [:payments [:id :method :for :autopay :amount :status :pstart :pend :paid_on]]]]]
              :on-success [:payment.sources.fetch/success]
              :on-failure [:payment.sources.fetch/failure]}}))

(reg-event-fx
 :payment.source.autopay/fetch
 [(path db/path)]
 (fn [{:keys [db]} _]
   {:graphql {:query [[:autopay_source [:id]]]
              :on-success [:payment.source.autopay.fetch/success]
              :on-failure [:payment.source.autopay.fetch/failure]}}))

(reg-event-fx
 :payment.source.autopay.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   ;;(let [source (get-in response [:data :payment_sources])]
     (tb/log response)
     {:db (assoc db :autopay-source nil)}))

(reg-event-fx
 :payment.source.autopay.fetch/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:dispatch [:graphql/notify-errors! response]}))

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


;; =============================================================================
;; Add Bank
;; =============================================================================


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
;; Add Card
;; =============================================================================

;; Cards have no `submit` event, as this is handled by the Stripe JS API.
;; We skip immediately to `success`, where we've
;; received a token for the new card from Stripe.

(reg-event-fx
 :payment.sources.add.card/save-stripe-token!
 (fn [{:keys [db]} [_ token]]
   {:dispatch-n [[:loading :payment.sources.add/card true]]
    :graphql
    {:mutation   [[:add_payment_source {:token token} [:id]]]
     :on-success [::create-card-source-success]
     :on-failure [::create-card-source-fail]}}))


(reg-event-fx
 ::create-card-source-success
 (fn [{:keys [db]} [_ response]]
   (let [account-id (get-in db [:config :account :id])]
     {:dispatch-n [[:loading :payment.sources.add/card false]
                   [:modal/hide :payment.source/add]
                   [:payment.sources/fetch account-id]]
      :route      (routes/path-for :profile.payment/sources
                                   :query-params {:source-id (get-in response [:data :add_payment_source :id])})})))

(reg-event-fx
 ::create-card-source-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :payment.sources.add/card false]
                 [:graphql/notify-errors! response]]}))


;; =============================================================================
;; Set the Default Source
;; =============================================================================

(reg-event-fx
 :payment.source/set-default!
 (fn [{:keys [db]} [_ id]]
   (tb/log id)
   {:dispatch-n [[:loading :payment.sources/deleting true]]
    :graphql
    {:mutation   [[:set_default_source {:id id} [:id]]]
     :on-success [::set-default-source-success]
     :on-failure [::set-default-source-fail]}}))


(reg-event-fx
 ::set-default-source-success
 (fn [{:keys [db]} [_ response]]
   (let [account-id (get-in db [:config :account :id])
         sources    (subscribe [:payment/sources])]
     ;;(tb/log response sources)
     {:dispatch-n [[:loading :payment.sources/deleting false]
                   [:notify/success "Account was set as default payment source."]
                   [:payment.sources/fetch account-id]]
      :route      (routes/path-for :profile.payment/sources
                                   :query-params {})})))

(reg-event-fx
 ::set-default-source-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :payment.sources/deleting false]
                 [:graphql/notify-errors! response]]}))


;; =============================================================================
;; Delete a Source
;; =============================================================================

(reg-event-fx
 :payment.source/delete!
 (fn [{:keys [db]} [_ id]]
   (tb/log id)
   {:dispatch-n [[:loading :payment.sources/deleting true]]
    :graphql
    {:mutation   [[:delete_payment_source {:id id} [:id]]]
     :on-success [::delete-source-success]
     :on-failure [::delete-source-fail]}}))

(reg-event-fx
 ::delete-source-success
 (fn [{:keys [db]} [_ response]]
   (let [account-id (get-in db [:config :account :id])
         sources    (subscribe [:payment/sources])]
     ;;(tb/log response sources)
     {:dispatch-n [[:loading :payment.sources/deleting false]
                   [:modal/hide :payment.source/remove]
                   [:notify/success "Account deleted successfully."]
                   [:payment.sources/fetch account-id]]
      :route      (routes/path-for :profile.payment/sources
                                   :query-params {})})))

(reg-event-fx
 ::delete-source-fail
 (fn [{:keys [db]} [_ response]]
   (tb/error response)
   {:dispatch-n [[:loading :payment.sources/deleting false]
                 [:graphql/notify-errors! response]]}))

;; =============================================================================
;; Misc
;; =============================================================================

(reg-event-fx
 :stripe/load-scripts
 (fn [_ [_ version]]
   {:load-scripts [(str "https://js.stripe.com/" (or version "v2") "/")]}))
