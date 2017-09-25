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
            [toolbelt.predicates :as p]))

;;; NOTE: Think about error handling. At present, errors will propogate at the
;;; field level, not the top level. This means that if a single request from
;;; Stripe fails, we'll get back partial results. This seems desirable.


;; =============================================================================
;; Helpers
;; =============================================================================


;; TODO: add to toolbelt.datomic
(defn- mapify [entity]
  (if (p/entityd? entity)
    (assoc (into {} entity) :db/id (:db/id entity))
    entity))

(s/fdef mapify
        :args (s/cat :entity-or-map (s/or :entity p/entityd? :map map?))
        :ret map?)


(def ^:private stripe-charge-key
  ::charge)


(defn- inject-charge
  "Assoc `stripe-charge` into the payment for use by subresolvers."
  [payment stripe-charge]
  (assoc (mapify payment) stripe-charge-key stripe-charge))


(defn- get-charge [payment]
  (let [v (get payment stripe-charge-key)]
    (if (p/throwable? v)
      (throw v)
      v)))


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


(defn payment-for
  "What is this payment for?"
  [{conn :conn} _ payment]
  (payment/payment-for (d/entity (d/db conn) (td/id payment))))


(defn payment-for2
  "What is this payment for?"
  [{conn :conn} _ payment]
  (payment/payment-for2 (d/db conn) payment))


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
                (or (when-let [d (order/desc order)]
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
        :otherwise               :other))
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
        (timbre/error t "failed to fetch charge for payment: %s"
                      (:db/id payment))
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


(defn- ensure-payment-allowed
  [db requester payment stripe-customer source]
  (cond
    (not (#{:payment.status/due :payment.status/failed}
          (payment/status payment)))
    (format "This payment has status %s; cannot pay!"
            (name (payment/status payment)))

    (not (and (= (payment/payment-for2 db payment) :payment.for/rent)
              (= (:object source) "bank_account")))
    "Only bank accounts can be used to pay rent."

    (not= (:customer source) (:id stripe-customer))
    "The source you are attempting to pay with is not yours."))


(defn- create-charge!
  "Create a charge for `payment` on Stripe."
  [{:keys [stripe conn]} account payment customer source-id]
  (let [license  (member-license/active (d/db conn) account)
        property (member-license/property license)
        desc     (format "%s's rent at %s" (account/full-name account) (property/name property))]
    (rch/create! stripe (int (* 100 (payment/amount payment))) source-id
                 :email (account/email account)
                 :description desc
                :customer-id (customer/id customer)
                :managed-account (member-license/rent-connect-id license))))


(defn pay-rent!
  [{:keys [stripe conn requester] :as ctx} {:keys [id source] :as params} _]
  (let [result   (resolve/resolve-promise)
        payment  (d/entity (d/db conn) id)
        customer (customer/by-account (d/db conn) requester)]
    (go
      (try
        (let [scus   (<!? (rcu/fetch stripe (customer/id customer)))
              source (<!? (rcu/fetch-source stripe (:id scus) source))]
          (if-let [error (ensure-payment-allowed (d/db conn) requester payment scus source)]
            (resolve/deliver! result nil {:message error})
            (let [charge-id (:id (<!? (create-charge! ctx requester payment customer (:id source))))]
              @(d/transact-async conn [(-> (payment/add-charge payment charge-id)
                                           (assoc :stripe/source-id (:id source))
                                           (assoc :payment/status :payment.status/pending)
                                           (assoc :payment/paid-on (java.util.Date.)))])
              (resolve/deliver! result (d/entity (d/db conn) id)))))
        (catch Throwable t
          (timbre/error t ::pay params)
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
   :payment/external-id external-id
   :payment/method      method
   :payment/status      status
   :payment/source      source
   :payment/autopay?    autopay?
   :payment/for         payment-for
   :payment/type        payment-for2
   :payment/description description
   :payment/property    property
   :payment/order       order
   ;; queries
   :payment/list        payments
   ;; mutations
   :payment/pay-rent!   pay-rent!
   })


;;; Attach a charge id to all invoices

(comment


  (do

    (do
      (require '[datomic.api :as d])
      (require '[toolbelt.async :refer [<!!?]])
      (require '[blueprints.models.payment :as payment])
      (require '[blueprints.models.member-license :as member-license])
      (require '[clojure.core.async :as async])
      (require '[ribbon.invoice :as ri])
      )


    (defn invoice-payments
      "Get the payments that have invoice ids that do not yet have charge ids."
      [db]
      (->> (d/q '[:find [?p ...]
                  :in $
                  :where
                  [?p :stripe/invoice-id _]
                  [?p :payment/amount _]
                  [(missing? $ ?p :stripe/charge-id)]]
                db)
           (map (partial d/entity db))))


    (defn get-charge-id
      [stripe db payment]
      (if-let [managed-account (and (payment/autopay? payment)
                                    (->> (payment/account payment)
                                         (member-license/active db)
                                         (member-license/rent-connect-id)))]
        (ri/fetch stripe (payment/invoice-id payment) :managed-account managed-account)
        (ri/fetch stripe (payment/invoice-id payment))))


    (defn migrate-invoices
      "Fetch all payments with `:stripe/invoice-id`, find their associated charge
     on Stripe, and set `:stripe/charge-id` on the payment."
      [stripe conn]
      (let [payments (invoice-payments (d/db conn))
            invoices (<!!? (->> (map #(get-charge-id stripe (d/db conn) %) payments)
                                (async/merge)
                                (async/into [])))]
        @(d/transact conn (map (fn [{charge :charge id :id}]
                                 {:db/id            [:stripe/invoice-id id]
                                  :stripe/charge-id charge})
                               invoices))))


    (migrate-invoices (odin.config/stripe-secret-key odin.config/config) odin.datomic/conn))


  (invoice-payments (d/db odin.datomic/conn))

  )
