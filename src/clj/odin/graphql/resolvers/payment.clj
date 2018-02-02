(ns odin.graphql.resolvers.payment
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [blueprints.models.payment :as payment]
            [blueprints.models.property :as property]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.service :as service]
            [clojure.core.async :as async :refer [>! chan go]]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [ribbon.charge :as rch]
            [ribbon.customer :as rcu]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!!? <!?]]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

;;; NOTE: Think about error handling. At present, errors will propogate at the
;;; field level, not the top level. This means that if a single request from
;;; Stripe fails, we'll get back partial results. This seems desirable.


;; =============================================================================
;; Helpers
;; =============================================================================


;; TODO: add to toolbelt.datomic
(defn- mapify [entity]
  (if (td/entityd? entity)
    (assoc (into {} entity) :db/id (:db/id entity))
    entity))

(s/fdef mapify
        :args (s/cat :entity-or-map (s/or :entity td/entityd? :map map?))
        :ret map?)


(def ^:private stripe-charge-key
  ::charge)


(defn- inject-charge
  "Assoc `stripe-charge` into the payment for use by subresolvers."
  [payment stripe-charge]
  (assoc (mapify payment) stripe-charge-key stripe-charge))


(defn- get-charge [payment]
  (let [v (get payment stripe-charge-key)]
    (if (tb/throwable? v)
      (throw v)
      v)))


(defn overdue? [payment]
  (when-let [due-date (payment/due payment)]
    (t/after? (t/now) (c/to-date-time due-date))))


(defn- rent-late-fee [payment]
  (if-let [license (:member-license/_rent-payments payment)]
    (if (and (overdue? payment) (member-license/grace-period-over? license))
      (* (payment/amount payment) 0.1)
      0)
    0))


;; =============================================================================
;; Fields
;; =============================================================================


(defn autopay?
  "Is this an autopay payment?"
  [_ _ payment]
  (payment/autopay? payment))


(defn external-id
  "Retrieve the external id for this payment, if any."
  [_ _ payment]
  (or (payment/charge-id payment) (payment/invoice-id)))


(defn late-fee
  "Any late fees associated with this payment."
  [{conn :conn} _ payment]
  (let [payment (d/entity (d/db conn) (:db/id payment))]
    (when (= :payment.for/rent (payment/payment-for2 (d/db conn) payment))
      (rent-late-fee payment))))


(defn payment-for
  "What is this payment for?"
  [{conn :conn} _ payment]
  (when-some [x (payment/payment-for2 (d/db conn) payment)]
    (keyword (name x))))


(defn- deposit-desc
  "Description for a security deposit payment."
  [account payment]
  (let [deposit   (deposit/by-account account)
        first-py? (or (= (count (deposit/payments deposit)) 1)
                      (= (td/id payment)
                         (->> (deposit/payments deposit) (sort-by :payment/amount <) first td/id)))]
    (cond
      (= :deposit.type/full (deposit/type deposit)) "entire security deposit payment"
      first-py?                                     "first security deposit installment"
      :otherwise                                    "second security deposit installment")))


(defn description
  "A description of this payment. Varies based on payment type."
  [{conn :conn} _ payment]
  (let [payment (d/entity (d/db conn) (td/id payment))] ; ensure we're working with an entity
    (letfn [(-rent-desc [payment]
              (->> [(payment/period-start payment) (payment/period-end payment)]
                   (map date/short-date)
                   (apply format "rent for %s-%s")))
            (-order-desc [payment]
              (let [order        (order/by-payment (d/db conn) payment)
                    service-desc (service/name (order/service order))]
                (or (when-let [d (order/summary order)]
                      (format "%s (%s)" d service-desc))
                    service-desc)))]
      (case (payment/payment-for payment)
        :payment.for/deposit (deposit-desc (payment/account payment) payment)
        :payment.for/rent    (-rent-desc payment)
        :payment.for/order   (-order-desc payment)
        nil))))


(defn- charge-method
  [stripe-charge]
  (case (get-in stripe-charge [:source :object])
    "bank_account" :ach
    "card"         :card
    :other))


(defn method
  "The method with which this payment was made."
  [_ _ payment]
  (try
    (let [charge (get-charge payment)]
      (cond
        (:payment/check payment) :check
        (some? charge)           (charge-method charge)
        (payment/paid? payment)  :other
        :otherwise               nil))
    (catch Throwable t
      (resolve/resolve-as :unknown {:message  (.getMessage t)
                                    :err-data (ex-data t)}))))


(defn status
  "The status of this payment."
  [_ _ payment]
  (keyword (name (payment/status payment))))


(defn source
  "The payment source used to make this payment, if any."
  [_ _ payment]
  (try
    (-> payment get-charge :source)
    (catch Throwable t
      (resolve/resolve-as {} {:message  (.getMessage t)
                              :err-data (ex-data t)}))))


(defn order
  "The order associated with this payment, if any."
  [{conn :conn} _ payment]
  (order/by-payment (d/db conn) payment))


(defn property
  "The property associated with the account that made this payment, if any."
  [{conn :conn} _ payment]
  (let [created (td/created-at (d/db conn) payment)
        license (member-license/active (d/as-of (d/db conn) created)
                                       (payment/account payment))]
    (member-license/property license)))


;; =============================================================================
;; Queries
;; =============================================================================


;; =============================================================================
;; Payments + Stripe


(defn- managed-account
  "Produce the Stripe managed account if the payment was made one one."
  [db payment]
  (when (payment/autopay? payment)
    (->> (payment/account payment)
         (member-license/active db)
         (member-license/rent-connect-id))))


(defn- fetch-charge
  "Fetch the charge from the correct place (possibly a managed account). Returns
  a channel on which the result of the fetch operation will be put."
  [{:keys [stripe conn]} payment]
  (let [charge-id (payment/charge-id payment)]
    (if-let [managed (managed-account (d/db conn) payment)]
      (rch/fetch stripe charge-id :managed-account managed)
      (rch/fetch stripe charge-id))))


(defn process-payment
  [ctx payment out]
  (go
    (try
      (if-let [charge-id (payment/charge-id payment)]
        (let [charge (<!? (fetch-charge ctx payment))]
          (>! out (inject-charge payment charge)))
        (>! out payment))
      (catch Throwable t
        (timbre/errorf t "failed to fetch charge for payment: %s" (:db/id payment))
        (>! out payment)))
    (async/close! out)))


(def ^:private concurrency
  5)


(defn merge-stripe-data
  "Provided context `ctx` and `payments`, fetch all required data from Stripe
  and merge it into the payments."
  [{:keys [stripe] :as ctx} payments]
  (let [in  (chan)
        out (chan)]
    (async/pipeline-async concurrency out (partial process-payment ctx) in)
    (async/onto-chan in payments)
    (async/into [] out)))


(defn- parse-gql-params
  [{:keys [statuses types] :as params}]
  (tb/assoc-when
   params
   :statuses (when-some [xs statuses]
               (map #(keyword "payment.status" (name %)) xs))
   :types (when-some [xs types]
            (map #(keyword "payment.for" (name %)) xs))))


(defn- query-payments
  "Query payments for `account` from `db`."
  [db params]
  (->> (parse-gql-params params)
       (apply concat)
       (apply payment/query db)
       (sort-by :payment/paid-on)
       (reverse)
       (map mapify)))


;; =============================================================================
;; Query


(defn payments
  "Query payments based on `params`."
  [{:keys [conn] :as ctx} {params :params} _]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [payments (query-payments (d/db conn) params)]
          (resolve/deliver! result (<!? (merge-stripe-data ctx payments))))
        (catch Throwable t
          (timbre/error t ::payments params)
          (resolve/deliver! result nil {:message  (.getMessage t)
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn- cents [x]
  (int (* 100 x)))



;; =============================================================================
;; Create Payments


(defmulti create-payment-data (fn [_ params] (:type params)))


(defmethod create-payment-data :default [db _] nil)


(defn- default-due-date
  "The default due date is the fifth day of the same month as `start` date.
  Preserves the original year, month, hour, minute and second of `start` date."
  [start]
  (let [st (c/to-date-time start)]
    (c/to-date (t/date-time (t/year st)
                            (t/month st)
                            5
                            (t/hour st)
                            (t/minute st)
                            (t/second st)))))


(defmethod create-payment-data :rent [db {:keys [month amount account]}]
  (when (nil? month)
    (resolve/resolve-as nil {:message "When payment type is rent, month must be specified."}))
  (let [account (d/entity db account)
        ml      (member-license/active db account)
        tz      (member-license/time-zone ml)
        start   (date/beginning-of-month month tz)
        payment (payment/create amount account
                                :for :payment.for/rent
                                :pstart start
                                :pend (date/end-of-month month tz)
                                :due (-> (default-due-date start) (date/end-of-day tz)))]
    [payment (member-license/add-rent-payments ml payment)]))


(defmethod create-payment-data :deposit [db {:keys [amount account]}]
  (let [account (d/entity db account)
        deposit (deposit/by-account account)
        payment (payment/create amount account :for :payment.for/deposit)]
    [payment (deposit/add-payment deposit payment)]))


(defn create-payment!
  [{:keys [conn] :as ctx} {params :params} _]
  (if-let [tx-data (create-payment-data (d/db conn) params)]
    (do
      @(d/transact conn tx-data)
      (payment/by-id (d/db conn) (payment/id (first tx-data))))
    (resolve/resolve-as nil {:message "Cannot create payment with specified params."
                             :params  params})))


;; =============================================================================
;; Pay Rent


(defn- ensure-payment-allowed
  [db requester payment source]
  (cond
    (not (#{:payment.status/due :payment.status/failed}
          (payment/status payment)))
    (format "This payment has status %s; cannot pay!"
            (name (payment/status payment)))

    (not (and (= (payment/payment-for2 db payment) :payment.for/rent)
              (= (:object source) "bank_account")))
    "Only bank accounts can be used to pay rent."))


(defn- create-bank-charge!
  "Create a charge for `payment` on Stripe."
  [{:keys [stripe conn]} account payment customer source-id]
  (let [license  (member-license/active (d/db conn) account)
        property (member-license/property license)
        amount   (cents (+ (payment/amount payment) (rent-late-fee payment)))
        desc     (format "%s's rent at %s" (account/full-name account) (property/name property))]
    (rch/create! stripe amount source-id
                 :email (account/email account)
                 :description desc
                 :application-fee (int (* (/ (property/ops-fee property) 100) amount))
                 :customer-id (customer/id customer)
                 :destination (member-license/rent-connect-id license))))


(defn pay-rent!
  [{:keys [stripe conn requester] :as ctx} {:keys [id source] :as params} _]
  (let [result   (resolve/resolve-promise)
        payment  (d/entity (d/db conn) id)
        customer (customer/by-account (d/db conn) requester)]
    (go
      (try
        (let [source (<!? (rcu/fetch-source stripe (customer/id customer) source))]
          (if-let [error (ensure-payment-allowed (d/db conn) requester payment source)]
            (resolve/deliver! result nil {:message error})
            (let [charge-id (:id (<!? (create-bank-charge! ctx requester payment customer (:id source))))]
              @(d/transact-async conn [(-> (payment/add-charge payment charge-id)
                                           (assoc :stripe/source-id (:id source))
                                           (assoc :payment/status :payment.status/pending)
                                           (assoc :payment/paid-on (java.util.Date.)))])
              (resolve/deliver! result (d/entity (d/db conn) id)))))
        (catch Throwable t
          (timbre/error t ::pay-rent params)
          (resolve/deliver! result nil {:message  (.getMessage t)
                                        :err-data (ex-data t)}))))
    result))


(comment

  (let [stripe    (odin.config/stripe-secret-key odin.config/config)
        conn      odin.datomic/conn
        requester (d/entity (d/db conn) [:account/email "member@test.com"])
        payment   (d/entity (d/db conn) 285873023223183)
        customer  (customer/by-account (d/db conn) requester)
        scus      (<!!? (rcu/fetch stripe (customer/id customer)))
        source    (<!!? (rcu/fetch-source stripe (:id scus) "ba_1AjwecIvRccmW9nO175kwr0e"))
        owner     (payment/account payment)]
    (ensure-payment-allowed (d/db conn) requester payment scus source))


  )


;; =============================================================================
;; Pay Deposit


(defn pay-deposit!
  [{:keys [stripe conn requester] :as ctx} {source-id :source} _]
  (let [result   (resolve/resolve-promise)
        deposit  (deposit/by-account requester)
        customer (customer/by-account (d/db conn) requester)]
    (go
      (try
        (let [source (<!? (rcu/fetch-source stripe (customer/id customer) source-id))]
          (if (= (:object source) "bank_account")
            (let [payment (payment/create (deposit/amount-remaining deposit) requester
                                          :for :payment.for/deposit
                                          :source-id source-id)
                  charge  (<!? (create-bank-charge! ctx requester payment customer source-id))]
              @(d/transact-async conn [(assoc payment
                                              :stripe/charge-id (:id charge)
                                              :payment/paid-on (java.util.Date.))
                                       (deposit/add-payment deposit payment)])
              (resolve/deliver! result (d/entity (d/db conn) (:db/id deposit))))
            (resolve/deliver! result nil {:message "Only bank accounts can be used to pay your deposit."})))
        (catch Throwable t
          (timbre/error t ::pay-deposit {:id (:db/id requester) :source source})
          (resolve/deliver! result nil {:message  (.getMessage t)
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :payment/list [_ account params]
  (or (account/admin? account)
      (= (:db/id account) (get-in params [:params :account]))))


(defmethod authorization/authorized? :payment/pay-rent!
  [{conn :conn} account params]
  (let [payment (d/entity (d/db conn) (:id params))]
    (= (:db/id account) (:db/id (payment/account payment)))))


(def resolvers
  {;; fields
   :payment/external-id  external-id
   :payment/late-fee     late-fee
   :payment/method       method
   :payment/status       status
   :payment/source       source
   :payment/autopay?     autopay?
   :payment/type         payment-for
   :payment/description  description
   :payment/property     property
   :payment/order        order
   ;; queries
   :payment/list         payments
   ;; mutations
   :payment/create!      create-payment!
   :payment/pay-rent!    pay-rent!
   :payment/pay-deposit! pay-deposit!
   })
