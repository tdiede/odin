(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.customer :as customer]
            [blueprints.models.payment :as payment]
            [blueprints.models.property :as property]
            [clojure.core.async :as async :refer [go <! >! chan]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.config :as config]
            [odin.graphql.resolvers.payment :as payment-resolvers]
            [odin.graphql.resolvers.utils :refer [context?]]
            [ribbon.charge :as rch]
            [ribbon.customer :as rcu]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!? go-try]]
            [toolbelt.core :as tb]
            [toolbelt.predicates :as p]
            [clojure.spec.alpha :as s]
            [blueprints.models.account :as account]))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- bank-account? [source]
  (some? (#{"bank_account"} (:object source))))


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


(defn- merge-autopay-payments
  [{:keys [conn stripe]} source]
  (go
    (let [db      (d/db conn)
          account (customer/account (customer/by-customer-id db (:customer source)))]
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
  [{:keys [conn] :as ctx} source]
  (if-not (bank-account? source)
    (go (query-payments (d/db conn) (:id source)))
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
;; Queries
;; =============================================================================


(defn- sources-by-account
  "Produce all payment sources for a given `account`."
  [{:keys [conn stripe]} account]
  (let [customer (customer/by-account (d/db conn) account)
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
  [{:keys [conn] :as context} {:keys [account]} _]
  (let [account (d/entity (d/db conn) account)]
    ;; NOTE: We may also provide the capability to supply customer-id.
    (sources-by-account context account)))


;; =============================================================================
;; Mutations
;; =============================================================================


;; =============================================================================
;; Delete


(defn- fetch-source
  "Fetch the source by fetching the `requester`'s customer entity and attempting
  to fetch the source present on it. This mandates that the `source-id` actually
  belong to the requesting account."
  [{:keys [requester stripe conn] :as ctx} source-id]
  (let [customer (customer/by-account (d/db conn) requester)]
    (rcu/fetch-source stripe (customer/id customer) source-id)))


(defn- autopay-source
  "Fetch the autopay source for the requesting user, if there is one."
  [{:keys [stripe requester conn]}]
  (when-let [customer (customer/autopay (d/db conn) requester)]
    (let [managed (property/rent-connect-id (customer/managing-property customer))]
      (rcu/fetch-source stripe (customer/id customer) (customer/bank-token customer)
                        :managed-account managed))))

(s/fdef autopay-source
        :args (s/cat :ctx context?)
        :ret (s/or :chan p/chan? :nothing nil?))


(defn- delete-bank-source!*
  "Attempt to delete the bank source; if successful, remove the bank token from
  the Stripe customer. An exception will be put onto the `out` channel if
  unsuccessful."
  [{:keys [conn stripe]} source]
  (go-try
   (let [res (<!? (rcu/delete-source! stripe (:customer source) (:id source)))]
     @(d/transact-async conn [[:db/retract
                               [:stripe-customer/customer-id (:customer source)]
                               :stripe-customer/bank-account-token
                               (:id source)]])
     res)))

(s/fdef delete-bank-source!*
        :args (s/cat :ctx context? :source map?)
        :ret p/chan?)


(defn delete-bank-source!
  "Delete a bank source. Produces a channel that will contain an exception in
  the event of failure."
  [{:keys [requester] :as ctx} source]
  (go-try
   (if-let [c (autopay-source ctx)]
     (if (= (:fingerprint source) (:fingerprint (<!? c)))
       ;; if the fingerprints are equal, `source` is being used for autopay
       (throw (ex-info "Cannot delete source, as it's being used for autopay!"
                       {:source source}))
       ;; if they're not equal, this is not the autopay source; delete
       (<!? (delete-bank-source!* ctx)))
     ;; no autopay account, so source can be deleted
     (<!? (delete-bank-source!* ctx)))))


;; TODO: Test this after I add mechanism to add sources!
(defn delete-source!
  "Delete the `source`. Checks if source is a bank account, and if so, checks
  if it is also present on a connected account (autopay)."
  [{:keys [stripe] :as ctx} source]
  (go-try
   (if (bank-account? source)
     (<!? (delete-bank-source! ctx source))
     (<!? (rcu/delete-source! stripe (:customer source) (:id source))))))


(defn delete!
  "Delete the payment source with `id`. If the source is a bank account, will
  also delete it on the connected account."
  [ctx {id :id} _]
  (let [result (resolve/resolve-promise)]
    (go
      (let [source (<! (fetch-source ctx id))]
        (if (p/throwable? source)
          (resolve/deliver! result nil {:message  "Could not find source!"
                                        :err-data (ex-data source)})
          (try
            (<!? (delete-source! ctx source))
            (resolve/deliver! result nil id)
            (catch Throwable t
              (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                            :err-data (ex-data t)}))))))
    result))


;; =============================================================================
;; Add Bank Account


(defn- resolve-customer!
  "Produce the customer for `requester` if there is one; otherwise, createa a
  new customer."
  [{:keys [conn stripe requester]}]
  (go-try
   (if-let [customer (customer/by-account (d/db conn) requester)]
     customer
     (let [cus (<!? (rcu/create2! stripe (account/email requester)))]
       @(d/transact-async conn [(customer/create (:id cus) requester)])
       (customer/by-customer-id (d/db conn) (:id cus))))))


(defn add-bank!
  "Add a bank account source."
  [{:keys [conn stripe requester] :as ctx} {:keys [token]} _]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [customer (<!? (resolve-customer! ctx))
              source   (<!? (rcu/add-source! stripe (customer/id customer) token))]
          @(d/transact-async conn [[:db/add (:db/id customer) :stripe-customer/bank-account-token (:id source)]])
          (resolve/deliver! result nil source))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                        :err-data (ex-data t)}))))
    result))


(comment
  (let [conn odin.datomic/conn
        ctx  {:conn      conn
              :stripe    (odin.config/stripe-secret-key odin.config/config)
              :requester (d/entity (d/db conn) [:account/email "member@test.com"])}]
    )



  )



(comment
  (let [conn     odin.datomic/conn
        stripe   (odin.config/stripe-secret-key odin.config/config)
        account  (d/entity (d/db conn) [:account/email "member@test.com"])
        customer (customer/by-account (d/db conn) account)]
    ;; (async/<!! (rcu/fetch-source stripe (customer/id customer) "ba_1AV6tfIvRccmW9nOfjsLP6DZ"))
    ;; (async/<!! (rcu/fetch-source stripe (customer/id customer) "card_1AV6tzIvRccmW9nOhQsWMTuv"))
    (async/<!! (rcu/fetch-source stripe (customer/id customer) "unknown-source")))

  )


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
