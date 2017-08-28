(ns odin.graphql.resolvers.payment
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [clojure.core.async :as async :refer [>! chan go]]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [ribbon.charge :as rch]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!!? <!?]]
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
  [_ _ payment]
  (payment/payment-for payment))


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


(defn- query-payments
  "Query payments for `account` from `db`."
  [db account]
  (->> (payment/payments db account)
       (sort-by :payment/paid-on)
       (map mapify)))


(def ^:private concurrency
  5)


(defn merge-stripe-data
  "Provided context `ctx` and `payments`, fetch all required data from Stripe
  and merge it into the payments."
  [{:keys [stripe] :as ctx} payments]
  (let [in       (chan)
        out      (chan)]
    (async/pipeline-async concurrency out (partial process-payment ctx) in)
    (async/onto-chan in payments)
    (async/into [] out)))


;; =============================================================================
;; Query


(defn payments
  "Asynchronously fetch payments for `account_id` or the requesting user, if
  `account_id` is not supplied."
  [{:keys [conn] :as ctx} {:keys [account]} _]
  (let [db      (d/db conn)
        account (d/entity db account)
        result  (resolve/resolve-promise)]
    (go
      (try
        (let [payments (query-payments db account)]
          (resolve/deliver! result (<!? (merge-stripe-data ctx payments))))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                        :err-data (ex-data t)}))))
    result))


(comment

  (let [conn    odin.datomic/conn
        context {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester (d/entity (d/db conn) [:account/email "member@test.com"])}
        ]
    (map :payment/paid-on (query-payments (d/db conn) (:requester context))))

  )


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
