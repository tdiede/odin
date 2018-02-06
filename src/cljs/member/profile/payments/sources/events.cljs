(ns member.profile.payments.sources.events
  (:require [iface.utils.formatters :as format]
            [member.profile.payments.sources.db :as db]
            [member.routes :as routes]
            [re-frame.core :refer [path reg-event-db reg-event-fx]]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Routing/Nav
;; =============================================================================


(defmethod routes/dispatches :profile.payment/sources
  [{:keys [params requester] :as route}]
  (conj
   (if (or (empty? params) (= (:source-id params) ""))
     [[:payment.sources/set-default-route (:id requester)]]
     [[:payment.sources/init (:id requester) route]])
   [:member.license/fetch (:id requester)]))


(reg-event-fx
 :payment.sources/set-default-route
 [(path db/path)]
 (fn [{{sources :sources} :db} [_ account-id]]
   (if-let [source (first sources)]
     {:route (routes/path-for :profile.payment/sources :query-params {:source-id (:id source)})}
     {:dispatch [:payment.sources/fetch account-id true]})))


(reg-event-fx
 :payment.sources/init
 [(path db/path)]
 (fn [{{sources :sources} :db} [_ account-id {:keys [params]}]]
   (let [source-id (:source-id params)]
     {:dispatch-n [[:payment.sources/set source-id]
                   [:payment.sources/fetch account-id true]]})))


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
 (fn [{:keys [db]} [k account-id change-route?]]
   {:dispatch-n [[:ui/loading k true]
                 [:payment-sources/fetch account-id
                  {:on-success [:payment.sources.fetch/success k change-route?]}]]}))


(defn- active-source-should-change? [db sources]
  (and (not (empty? sources)) (nil? (:current db))))


(reg-event-fx
 :payment.sources.fetch/success
 [(path db/path)]
 (fn [{:keys [db]} [_ k change-route? sources :as v]]
   (let [route   (when (and change-route? (active-source-should-change? db sources))
                   (routes/path-for :profile.payment/sources
                                    :query-params {:source-id (:id (first sources))}))]
     (tb/assoc-when
      {:db       (assoc db :sources sources)
       :dispatch [:ui/loading k false]}
      :route route))))


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
     {:dispatch [:ui/loading :payment.sources.add/bank true]
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
   (timbre/error error)
   {:dispatch [:ui/loading :payment.sources.add/bank false]}))


(reg-event-fx
 ::create-bank-token-success
 (fn [_ [_ {token :id :as result}]]
   {:graphql
    {:mutation   [[:add_payment_source {:token token} [:id [:account [:id]]]]]
     :on-success [::create-bank-source-success :payment.sources.add/bank]
     :on-failure [:graphql/failure :payment.sources.add/bank]}}))


(reg-event-fx
 ::create-bank-source-success
 (fn [{:keys [db]} [_ k response]]
   (let [account-id (get-in response [:data :add_payment_source :account :id])]
     {:dispatch-n [[:ui/loading k false]
                   [:modal/hide :payment.source/add]
                   [:payment.sources/fetch account-id]]
      :route       (routes/path-for :profile.payment/sources
                                    :query-params {:source-id (get-in response [:data :add_payment_source :id])})})))


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
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:verify_bank_source {:deposits [amount-1 amount-2]
                                      :id       source-id}
                 [:id [:account [:id]]]]]
               :on-success [::bank-verify-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::bank-verify-success
 (fn [{db :db} [_ k response]]
   (let [account-id (get-in response [:data :verify_bank_source :account :id])]
     {:dispatch-n [[:ui/loading k false]
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
 (fn [_ [k token]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:add_payment_source {:token token} [:id]]]
               :on-success [::create-card-source-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::create-card-source-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:modal/hide :payment.source/add]]
    :route      (routes/path-for :profile.payment/sources
                                 :query-params {:source-id (get-in response [:data :add_payment_source :id])})}))


;; =============================================================================
;; Autopay
;; =============================================================================


(reg-event-fx
 :payment.sources.autopay/confirm-modal
 [(path db/path)]
 (fn [_ [_ is-enabled]]
   (if is-enabled
     {:dispatch [:modal/show :payment.source/autopay-disable]}
     {:dispatch [:modal/show :payment.source/autopay-enable]})))


(reg-event-fx
 :payment.sources.autopay/enable!
 [(path db/path)]
 (fn [{:keys [db]} [k source-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:set_autopay_source {:id source-id}
                             [:id [:account [:id]]]]]
               :on-success [::autopay-enable-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::autopay-enable-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (let [account-id (get-in response [:data :set_autopay_source :account :id])]
     {:dispatch-n [[:ui/loading k false]
                  [:payment.sources/fetch account-id]
                  [:modal/hide :payment.source/autopay-enable]
                  [:notify/success "Great! Autopay is now enabled."]]})))


(reg-event-fx
 :payment.sources.autopay/disable!
 [(path db/path)]
 (fn [_ [k source-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:unset_autopay_source {:id source-id}
                             [:id [:account [:id]]]]]
               :on-success [::autopay-disable-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::autopay-disable-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (let [account-id (get-in response [:data :unset_autopay_source :account :id])]
     {:dispatch-n [[:ui/loading k false]
                   [:payment.sources/fetch account-id]
                   [:modal/hide :payment.source/autopay-disable]]})))


;; =============================================================================
;; Set the Default Source
;; =============================================================================


(reg-event-fx
 :payment.source/set-default!
 (fn [{:keys [db]} [k id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:set_default_source {:id id}
                             [:id :name :last4 [:account [:id]]]]]
               :on-success [::set-default-source-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::set-default-source-success
 [(path db/path)]
 (fn [{:keys [db]} [_ k response]]
   (let [{:keys [name last4 account]} (get-in response [:data :set_default_source])]
     {:dispatch-n [[:ui/loading k false]
                   [:notify/success
                    (format/format "Great! We'll use your %s (%s) for service payments from now on."
                                   name last4)]
                   [:payment.sources/fetch (:id account)]]})))


;; =============================================================================
;; Delete a Source
;; =============================================================================


(reg-event-fx
 :payment.source/delete!
 (fn [{:keys [db]} [k id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:delete_payment_source {:id id} [:id]]]
               :on-success [::delete-source-success k id]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::delete-source-success
 [(path db/path)]
 (fn [{:keys [db]} [_ k source-id response]]
   (let [new-source (first (filter #(not= source-id (:id %)) (:sources db)))]
     {:dispatch-n [[:ui/loading k false]
                   [:modal/hide :payment.source/remove]
                   [:notify/success "Payment method deleted successfully."]]
      :route       (routes/path-for :profile.payment/sources
                                    :query-params {:source-id (:id new-source)})})))


;; =============================================================================
;; Misc
;; =============================================================================

(reg-event-fx
 :stripe/load-scripts
 (fn [_ [_ version]]
   {:load-scripts [(str "https://js.stripe.com/" (or version "v2") "/")]}))
