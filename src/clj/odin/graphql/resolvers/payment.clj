(ns odin.graphql.resolvers.payment
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [blueprints.models.payment :as payment]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.service :as service]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [teller.customer :as tcustomer]
            [teller.payment :as tpayment]
            [teller.source :as tsource]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]))

;; =============================================================================
;; Helpers
;; =============================================================================


(def ^:private stripe-charge-key
  ::charge)


(defn- inject-charge
  "Assoc `stripe-charge` into the payment for use by subresolvers."
  [payment stripe-charge]
  (assoc (td/mapify payment) stripe-charge-key stripe-charge))


(defn- get-charge [payment]
  (let [v (get payment stripe-charge-key)]
    (if (tb/throwable? v)
      (throw v)
      v)))


(defn- rent-late-fee [payment]
  (if-let [license (:member-license/_rent-payments payment)]
    (if (and (tpayment/overdue? payment) (member-license/grace-period-over? license))
      (* (payment/amount payment) 0.1)
      0)
    0))


;; =============================================================================
;; Fields
;; =============================================================================


(defn autopay?
  "Is this an autopay payment?"
  [_ _ payment]
  (and (some? (tpayment/subscription payment))
       (= :payment.type/rent (tpayment/type payment))))


(defn late-fee
  "Any late fees associated with this payment."
  [_ _ payment]
  )


(defn payment-type
  "What is this payment for?"
  [_ _ payment]
  (-> (tpayment/type payment)
      name
      (string/replace "-" "_")
      keyword))


(defn- deposit-desc
  "Description for a security deposit payment."
  [account payment]
  (let [deposit   (deposit/by-account account)
        first-py? (or (= (count (deposit/payments deposit)) 1)
                      (= (td/id payment)
                         (->> (deposit/payments deposit)
                              (map (juxt td/id tpayment/amount))
                              (sort-by second <)
                              ffirst)))]
    (cond
      (= :deposit.type/full (deposit/type deposit)) "entire security deposit payment"
      first-py?                                     "first security deposit installment"
      :otherwise                                    "second security deposit installment")))


(defn description
  "A description of this payment. Varies based on payment type."
  [{conn :conn} _ payment]
  (letfn [(-rent-desc [payment]
            (->> [(tpayment/period-start payment) (tpayment/period-end payment)]
                 (map date/short)
                 (apply format "rent for %s-%s")))
          (-order-desc [payment]
            (let [order        (order/by-payment (d/db conn) payment)
                  service-desc (service/name (order/service order))]
              (or (when-let [d (order/summary order)]
                    (format "%s (%s)" d service-desc))
                  service-desc)))]
    (case (tpayment/type payment)
      :payment.type/deposit (deposit-desc (tcustomer/account (tpayment/customer payment)) payment)
      :payment.type/rent    (-rent-desc payment)
      :payment.type/order   (-order-desc payment)
      nil)))


(defn method
  "The method with which this payment was made."
  [_ _ payment]
  (when-not (#{:payment.status/due} (tpayment/status payment))
    (let [source (tpayment/source payment)]
      (cond
        (tsource/bank-account? source) :ach
        (tsource/card? source)         :card
        (tpayment/check payment)       :check
        :else                          :other))))


(defn status
  "The status of this payment."
  [_ _ payment]
  )


(defn source
  "The payment source used to make this payment, if any."
  [_ _ payment]
  )


(defn order
  "The order associated with this payment, if any."
  [_ _ payment]
  )


(defn property
  "The property associated with the account that made this payment, if any."
  [_ _ payment]
  )

(comment

  (do
    (require '[com.walmartlabs.lacinia :refer [execute]])
    (require '[odin.datomic :refer [conn]])
    (require '[odin.teller :refer [teller]])
    (require '[datomic.api :as d])
    (require '[venia.core :as venia]))


  (let [customer (tcustomer/by-email teller "member@test.com")]
    (tpayment/query teller {:customers [customer]}))


  (let [account-id (:db/id (d/entity (d/db conn) [:account/email "member@test.com"]))]
    (execute odin.graphql/schema
             (venia/graphql-query
              {:venia/queries
               [[:payments {:params {:account account-id}}
                 [:autopay :type]]]})
             nil
             {:conn      conn
              :requester (d/entity (d/db conn) [:account/email "admin@test.com"])
              :teller    teller}))

  )

;; =============================================================================
;; Queries
;; =============================================================================


(defn- parse-gql-params
  [{:keys [teller]} {:keys [statuses types account] :as params}]
  (tb/assoc-when
   params
   :statuses (when-some [xs statuses]
               (map #(keyword "payment.status" (name %)) xs))
   :types (when-some [xs types]
            (map #(keyword "payment.type" (name %)) xs))
   :customers (when-some [a account]
                [(tcustomer/by-account teller a)])))


;; =============================================================================
;; Query


(defn payments
  "Query payments based on `params`."
  [{:keys [teller] :as ctx} {params :params} _]
  (try
    (tpayment/query teller (parse-gql-params ctx params))
    (catch Throwable t
      (timbre/error t ::query params)
      (resolve/resolve-as nil {:message  (.getMessage t)
                               :err-data (ex-data t)}))))





;; =============================================================================
;; Mutations
;; =============================================================================


;; =============================================================================
;; Create Payments


;; TODO move to Teller as a helper, may already be there
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


;; ;; TODO move this to teller
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


(defn create-payment!
  "TODO: FIXME"
  [_ _ _]
  )


;; make payments ===============================================================


;; TODO: How to deal with passing the fee?
;; TODO: How do we communicate what the fee will be to the person making the
;; payment?
;; TODO: We'll need to be able to update the fee amount before we make the
;; charge if they're going to be paying with a card
(defn pay-rent!
  [{:keys [stripe conn requester teller] :as ctx} {:keys [id source] :as params} _]
  (let [payment    (payment/by-id teller id)
        #_#_#_#_#_#_customer   (customer/by-account (d/db conn) requester)
        license    (member-license/active (d/db conn) requester)
        connect-id (member-license/rent-connect-id license)]
    #_(go
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
                                        :err-data (ex-data t)}))))))


;; =============================================================================
;; Pay Deposit


;; TODO: Refactor using above as reference
(defn pay-deposit!
  [{:keys [stripe conn requester] :as ctx} {source-id :source} _]
  #_(let [result     (resolve/resolve-promise)
        deposit    (deposit/by-account requester)
        license    (member-license/active (d/db conn) requester)
        connect-id (member-license/deposit-connect-id license)
        customer   (customer/by-account (d/db conn) requester)]
    (go
      (try
        (tpayment/create! customer (deposit/amount-remaining deposit) :payment.type/deposit)
        (catch Throwable t
          (timbre/error t ::pay-deposit {:id (:db/id requester) :source-id source-id})
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
   :payment/id           (fn [_ _ payment] (tpayment/id payment))
   :payment/entity-id    (fn [_ _ payment] (td/id payment))
   :payment/late-fee     late-fee
   :payment/method       method
   :payment/status       status
   :payment/source       source
   :payment/autopay?     autopay?
   :payment/type         payment-type
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
