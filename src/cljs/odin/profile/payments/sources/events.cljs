(ns odin.profile.payments.sources.events
  (:require [odin.profile.payments.sources.db :as db]
            [odin.profile.payments.sources.autopay :as autopay]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   subscribe
                                   reg-event-fx
                                   dispatch
                                   path debug]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Routing/Nav
;; =============================================================================


(defmethod routes/dispatches :profile.payment/sources
  [{:keys [params requester] :as route}]
  (if (or (empty? params) (= (:source-id params) ""))
    [[:payment.sources/set-default-route]]
    [[:payment.sources/init route]]))


(reg-event-fx
 :payment.sources/set-default-route
 [(path db/path)]
 (fn [{{sources :sources} :db} _]
   (if-let [source (first sources)]
     {:route (routes/path-for :profile.payment/sources :query-params {:source-id (:id source)})}
     {:dispatch [:payment.sources/fetch]})))


(reg-event-fx
 :payment.sources/init
 [(path db/path)]
 (fn [{{sources :sources} :db} [_ {:keys [params]}]]
   (let [source-id (:source-id params)]
     (if (empty? sources)
       {:dispatch-n [[:payment.sources/set source-id]
                     [:payment.sources/fetch]]}
       {:dispatch [:payment.sources/set source-id]}))))


(reg-event-db
 :payment.sources/set
 [(path db/path)]
 (fn [db [_ current-source-id]]
   (assoc db :current current-source-id)))


;; =============================================================================
;; Fetch Sources
;; =============================================================================


(reg-event-fx
 :payment.sources/fetch
 (fn [{:keys [db]} _]
   (let [account-id (get-in db [:config :account :id])]
     {:dispatch [:loading :payment.sources/fetch true]
      :graphql  {:query
                 [[:payment_sources {:account account-id}
                   [:id :last4 :customer :type :name :status :default :autopay :expires
                    [:payments [:id :method :for :autopay :amount :status :pstart :pend :paid_on :description]]]]]
                 :on-success [:payment.sources.fetch/success]
                 :on-failure [:payment.sources.fetch/failure]}})))


(defn- active-source-should-change? [db sources]
  (and (not (empty? sources)) (nil? (:current db))))


(reg-event-fx
 :payment.sources.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   (let [sources (get-in response [:data :payment_sources])
         route   (when (active-source-should-change? db sources)
                   (routes/path-for :profile.payment/sources
                                    :query-params {:source-id (:id (first sources))}))]
     (tb/log response)
     (tb/assoc-when
      {:db       (assoc db :sources sources)
       :dispatch [:loading :payment.sources/fetch false]}
      :route route))))
      ;; :dispatch [:payment.sources.autopay/parse payment-sources]



(reg-event-fx
 :payment.sources.fetch/failure
 [(path db/path)]
 (fn [_ [_ response]]
   {:dispatch-n [[:graphql/notify-errors! response]
                 [:loading :payment.sources/fetch false]]}))


(reg-event-fx
 :payment.sources.autopay/parse
 [(path db/path)]
 (fn [{:keys [db]} [_ sources]]
   (if-let [auto-source (autopay/get-autopay-source sources)]
     {:db (-> (assoc-in db [:autopay :source] (:id auto-source))
              (assoc-in [:autopay :on] true))}
     {:db (-> (assoc-in db [:autopay :source] nil)
              (assoc-in [:autopay :on] false))})))
;;:dispatch [:graphql/notify-errors! response]}))




;;(reg-event-fx
;; :payment.source.autopay/fetch
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   {:graphql {:query [[:autopay_source [:id]]]
;;              :on-success [:payment.source.autopay.fetch/success]
;;              :on-failure [:payment.source.autopay.fetch/failure]}}))
;;
;;(reg-event-fx
;; :payment.source.autopay.fetch/success
;; [(path db/path)]
;; (fn [{:keys [db]} [_ response]]
;;   ;;(let [source (get-in response [:data :payment_sources])]
;;     ;;(tb/log response)
;;     {:db (assoc db :autopay-source nil)}))
;;
;;(reg-event-fx
;; :payment.source.autopay.fetch/failure
;; [(path db/path)]
;; (fn [{:keys [db]} [_ response]]
;;   {:dispatch [:graphql/notify-errors! response]}))

;; (defn if-bank-enable-autopay
;;   [source]
;;   (if (= :bank (:type source))
;;     (update source :autopay true)
;;     source))

;; (defn set-autopay-on-banks
;;   [sources]
;;   (into {} (for [[k v] sources] [k (if-bank-enable-autopay v)])))

;; =============================================================================
;; Add Source
;; =============================================================================


(reg-event-db
 :payment.sources.add/select-type
 [(path db/add-path)]
 (fn [db [_ type]]
   (assoc db :type type)))


;; =============================================================================
;; Bank Accounts
;; =============================================================================


;; =============================================================================
;; Add


(reg-event-db
 :payment.sources.add.bank/update!
 [(path db/add-path)]
 (fn [db [_ k v]]
   (assoc-in db [:bank k] v)))


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
   {:graphql
    {:mutation   [[:add_payment_source {:token token} [:id]]]
     :on-success [::create-bank-source-success :payment.sources.add/bank]
     :on-failure [:graphql/failure :payment.sources.add/bank]}}))


(reg-event-fx
 ::create-bank-source-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:modal/hide :payment.source/add]
                 [:payment.sources/fetch]]
    :route      (routes/path-for :profile.payment/sources
                                 :query-params {:source-id (get-in response [:data :add_payment_source :id])})}))


;; =============================================================================
;; Verify

(reg-event-db
 :payment.sources.bank.verify/edit-amount
 [(path db/add-path)]
 (fn [db [_ k v]]
   (assoc-in db [:microdeposits k] v)))

(reg-event-fx
 :payment.sources.bank/verify!
 [(path db/path)]
 (fn [_ [k source-id amount-1 amount-2]]
   (tb/log source-id amount-1 amount-2)
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:verify_bank_source {:deposits [amount-1 amount-2]
                                      :id       source-id}
                 [:id]]]
               :on-success [::bank-verify-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::bank-verify-success
 (fn [{db :db} [_ k _]]
   (let [account-id (get-in db [:config :account :id])]
     {:dispatch-n [[:loading k false]
                   [:payment.sources/fetch account-id]
                   [:modal/hide :payment.source/verify-account]]})))


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
   {:dispatch-n [[:loading :payment.sources.add/card false]
                 [:modal/hide :payment.source/add]
                 [:payment.sources/fetch]]
    :route      (routes/path-for :profile.payment/sources
                                 :query-params {:source-id (get-in response [:data :add_payment_source :id])})}))

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
                   [:payment.sources/fetch]]
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
                   [:payment.sources/fetch]]
      :route      (routes/path-for :profile.payment/sources
                                   :query-params { :source-id (:id (first @sources))})})))


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
