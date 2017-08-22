(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.customer :as customer]
            [blueprints.models.payment :as payment]
            [clojure.core.async :as async :refer [go]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.config :as config]
            [odin.graphql.resolvers.payment :as payment-resolvers]
            [ribbon.charge :as rch]
            [ribbon.customer :as rcu]
            [toolbelt.async :refer [<!?]]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [clojure.spec.alpha :as s]
            [blueprints.models.property :as property]
            [toolbelt.datomic :as td]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn type
  "The type of source, #{:bank :card}."
  [_ _ source]
  (case (:object source)
    "bank_account" :bank
    "card" :card
    (resolve/resolve-as :unknown {:message (format "Unrecognized source type '%s'" (:object source))})))


(defn name
  "The name of this source."
  [_ _ source]
  (case (:object source)
    "bank_account" (:bank_name source)
    "card"         (:brand source)
    "unknown"))


(defn- query-payments [db & source-ids]
  (->> (d/q '[:find [?p ...]
              :in $ [?source-id ...]
              :where
              [?p :stripe/source-id ?source-id]]
            db source-ids)

       (map (partial d/entity db))))


(defn- bank-account? [source]
  (some? (#{"bank_account"} (:object source))))


(defn- merge-autopay-payments
  [{:keys [db stripe]} source]
  (go
    (let [account (customer/account (customer/by-customer-id db (:customer source)))]
      (try
        (if-let [ap-cus (customer/autopay db account)] ; if there's an autopay account...
          ;; there's an autopay account...get the payments
          (let [managed  (property/rent-connect-id (customer/managing-property ap-cus))
                customer (<!? (rcu/fetch stripe (customer/id ap-cus)
                                         :managed-account managed))
                sources  (rcu/sources customer)]
            ;; It's still possible that the bank account we're looking at is
            ;; different from the one linked to the managed account--use the
            ;; `:fingerprint` attribute to find out
            (if-let [ap-source (tb/find-by (comp #{(:fingerprint source)} :fingerprint) sources)]
              (query-payments db (:id ap-source) (:id source))
              (query-payments db (:id source))))
          ;; no autopay account, so no autopay payments
          [])
        (catch Throwable t
          (timbre/error t ::merge-autopay-payments
                        {:source   (:id source)
                         :customer (:customer source)
                         :account  (:db/id account)
                         :email    (:account/email account)})
          [])))))


(defn- get-payments
  [{:keys [db] :as ctx} source]
  (if-not (bank-account? source)
    (go (query-payments db (:id source)))
    (merge-autopay-payments ctx source)))


(defn payments
  "Payments associated with this `source`."
  [ctx _ source]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [payments (<!? (get-payments ctx source))]
          (resolve/deliver! result (<!? (payment-resolvers/merge-stripe-data ctx payments))))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Fields
;; =============================================================================


(defn- sources-by-account
  "Produce all payment sources for a given `account`."
  [{:keys [db stripe]} account]
  (let [customer (customer/by-account db account)
        result   (resolve/resolve-promise)]
    (go
      (try
        (let [customer' (<!? (rcu/fetch stripe (customer/id customer)))]
          (resolve/deliver! result (rcu/sources customer')))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                        :err-data (ex-data t)}))))
    result))


(defn sources
  "Retrieve payment sources."
  [{:keys [db] :as context} {:keys [account]} _]
  (let [account (d/entity db account)]
    ;; NOTE: We may also provide the capability to supply customer-id.
    (sources-by-account context account)))


;;; Attach a charge id to all invoices

(comment


  (let [conn odin.datomic/conn
        account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (customer/autopay (d/db conn) account))


  (do

    (do
      (require '[datomic.api :as d])
      (require '[toolbelt.async :refer [<!!?]])
      (require '[blueprints.models.payment :as payment])
      (require '[blueprints.models.member-license :as member-license])
      (require '[clojure.core.async :as async])
      (require '[ribbon.charge :as rch])
      )


    (defn payments
      "Get the payments that do not yet have source ids."
      [db]
      (->> (d/q '[:find [?p ...]
                  :in $
                  :where
                  [?p :payment/amount _]
                  [?p :stripe/charge-id _]
                  [(missing? $ ?p :stripe/source-id)]]
                db)
           (map (partial d/entity db))))


    (defn get-charge
      [stripe db payment]
      (if-let [managed-account (and (payment/autopay? payment)
                                    (->> (payment/account payment)
                                         (member-license/active db)
                                         (member-license/rent-connect-id)))]
        (rch/fetch stripe (payment/charge-id payment) :managed-account managed-account)
        (rch/fetch stripe (payment/charge-id payment))))


    (defn migrate-charges
      "Fetch all payments with `:stripe/charge-id`, find their associated charge
     on Stripe, and set `:stripe/source-id` on the payment."
      [stripe conn]
      (let [payments (payments (d/db conn))
            charges (<!!? (->> (map #(get-charge stripe (d/db conn) %) payments)
                               (async/merge)
                               (async/into [])))]
        @(d/transact conn (map (fn [charge]
                                 {:db/id            [:stripe/charge-id (:id charge)]
                                  :stripe/source-id (get-in charge [:source :id])})
                               charges))))


    (migrate-charges (odin.config/stripe-secret-key odin.config/config) odin.datomic/conn))


  )
