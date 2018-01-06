(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [blueprints.models.property :as property]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.core.async :as async :refer [<! go]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.config :as config]
            [odin.graphql.authorization :as authorization]
            [odin.graphql.resolvers.payment :as payment-resolvers]
            [odin.graphql.resolvers.utils :refer [context?]]
            [ribbon.charge :as rch]
            [ribbon.connect :as rcn]
            [ribbon.customer :as rcu]
            [ribbon.plan :as rp]
            [ribbon.subscription :as rs]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!? go-try]]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.predicates :as p]
            [odin.models.autopay :as autopay]
            [odin.models.payment-source :as payment-source]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn- bank-account? [source]
  (some? (#{"bank_account"} (:object source))))


(defn- error-message [t]
  (or (:message (ex-data t)) (.getMessage t)))

(s/fdef error-message
        :args (s/cat :throwable p/throwable?)
        :ret string?)


(defn- source-customer [db source]
  (let [customer-id (d/q '[:find ?e .
                           :in $ ?customer-id
                           :where
                           [?e :stripe-customer/customer-id ?customer-id]]
                         db (:customer source))]
    (d/entity db [:stripe-customer/customer-id (:customer source)])))


;; =============================================================================
;; Fields
;; =============================================================================


(defn account
  "The account that owns this payment source."
  [{conn :conn} _ source]
  ;; find a payment that was used with this source
  (try
    (payment-source/source-account (d/db conn) source)
    (catch Throwable t
      (timbre/error t)
      (resolve/resolve-as nil {:message "Something went wrong!"}))))


(defn autopay?
  "Is this source being used for autopay?"
  [{:keys [conn stripe]} _ source]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [[is-source _] (<!? (autopay/is-autopay-source? (d/db conn) stripe source))]
          (resolve/deliver! result is-source))
        (catch Throwable t
          ;; (resolve/deliver! result nil))))
          (resolve/deliver! result nil {:message (error-message t)
                                        :err-data (ex-data t)}))))
    result))


(defn default?
  "Is this source the default source?"
  [{:keys [conn stripe]} _ source]
  (let [result (resolve/resolve-promise)]
    (if-let [customer (::customer source)]
      (resolve/deliver! result (= (:id source) (:default_source customer)))
      (go
        (try
          (let [cus-ent  (source-customer (d/db conn) source)
                customer (<!? (rcu/fetch stripe (customer/id cus-ent)))]
            (resolve/deliver! result (= (:id source) (:default_source customer))))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


(defn type
  "The type of source, #{:bank :card}."
  [_ _ source]
  (case (:object source)
    "bank_account" :bank
    "card"         :card
    (resolve/resolve-as :unknown {:message (format "Unrecognized source type '%s'" (:object source))})))


(defn expiration
  "Returns the expiration date for a credit card. Returns nil if bank."
  [_ _ source]
  (when-let [year (:exp_year source)]
    (str (:exp_month source) "/" year)))


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
       (map (partial d/entity db))
       (sort-by :payment/paid-on)
       (reverse)))


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
          (query-payments db (:id source)))
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
          (resolve/deliver! result nil {:message  (error-message t)
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Queries
;; =============================================================================


(defn sources
  "Retrieve payment sources."
  [{:keys [conn stripe] :as context} {:keys [account]} _]
  (let [account  (d/entity (d/db conn) account)
        customer (customer/by-account (d/db conn) account)
        result   (resolve/resolve-promise)]
    (if (nil? customer)
      (resolve/deliver! result [])
      (go
        (try
          (resolve/deliver! result (<!? (payment-source/sources-by-account stripe customer)))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


;; =============================================================================
;; Mutations
;; =============================================================================


;; =============================================================================
;; Delete


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
  [{:keys [conn stripe] :as ctx} source]
  (go-try
   (if-let [c (autopay/autopay-source (d/db conn) stripe source)]
     (if (= (:fingerprint source) (:fingerprint (<!? c)))
       ;; if the fingerprints are equal, `source` is being used for autopay
       (throw (ex-info "Cannot delete source, as it's being used for autopay!"
                       {:source source}))
       ;; if they're not equal, this is not the autopay source; delete
       (<!? (delete-bank-source!* ctx source)))
     ;; no autopay account, so source can be deleted
     (<!? (delete-bank-source!* ctx source)))))


(defn delete-source!
  "Delete the `source`. Checks if source is a bank account, and if so, checks
  if it is also present on a connected account (autopay)."
  [{:keys [stripe] :as ctx} source]
  (go-try
   (if (bank-account? source)
     (<!? (delete-bank-source! ctx source)) ; DONE: WORKS WHEN NOT AUTOPAY!
     (<!? (rcu/delete-source! stripe (:customer source) (:id source))))))


(defn delete!
  "Delete the payment source with `id`. If the source is a bank account, will
  also delete it on the connected account."
  [{:keys [conn stripe requester] :as ctx} {id :id} _]
  (let [result (resolve/resolve-promise)]
    (go
      (let [source (<! (payment-source/fetch-source (d/db conn) stripe requester id))]
        (if (p/throwable? source)
          (resolve/deliver! result nil {:message  "Could not find source!"
                                        :err-data (ex-data source)})
          (try
            (<!? (delete-source! ctx source))
            (resolve/deliver! result source)
            (catch Throwable t
              (resolve/deliver! result nil {:message  (error-message t)
                                            :err-data (ex-data t)}))))))
    result))


;; =============================================================================
;; Add Source


(defn- fetch-or-create-customer!
  "Produce the customer for `requester` if there is one; otherwise, createa a
  new customer."
  [{:keys [conn stripe requester]}]
  (go-try
   (if-let [customer (customer/by-account (d/db conn) requester)]
     customer
     (let [cus (<!? (rcu/create2! stripe (account/email requester)))]
       @(d/transact-async conn [(customer/create (:id cus) requester)])
       (customer/by-customer-id (d/db conn) (:id cus))))))


(defn add-source!
  "Add a new source to the requester's Stripe customer, or create the customer
  and add the source if it doesn't already exist."
  [{:keys [conn stripe] :as ctx} {:keys [token]} _]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [customer (<!? (fetch-or-create-customer! ctx))
              cus      (<!? (rcu/fetch stripe (customer/id customer)))
              source   (<!? (rcu/add-source! stripe (customer/id customer) token))]
          ;; NOTE: Not sure that this is even necessary any longer.
          (when (= (:object source) "bank_account")
            @(d/transact-async conn [[:db/add (:db/id customer)
                                      :stripe-customer/bank-account-token (:id source)]]))
          (when (and (= (:object source) "card")
                     (not= (rcu/default-source-type cus) "card"))
            (<!? (rcu/update! stripe (:id cus) :default-source (:id source))))
          (resolve/deliver! result source))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (error-message t)
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Verify Bank Account


(s/def ::deposit (s/and pos-int? (partial > 100)))
(s/def ::deposits
  (s/cat :deposit-1 ::deposit
         :deposit-2 ::deposit))


(defn- deposits-valid? [deposits]
  (s/valid? ::deposits deposits))


(defn verify-bank!
  "Verify a bank account given the two microdeposit amounts that were made to
  the bank account."
  [{:keys [conn stripe requester] :as ctx} {:keys [id deposits]} _]
  (let [result (resolve/resolve-promise)]
    (if-not (deposits-valid? deposits)
      (resolve/deliver! result nil {:message  "Please provide valid deposit amounts."
                                    :deposits deposits})
      (go
        (try
          (let [[dep1 dep2]     deposits
                {sid :id
                 cid :customer} (<!? (payment-source/fetch-source (d/db conn) stripe requester id))
                bank            (<!? (rcu/verify-bank-account! stripe cid sid dep1 dep2))]
            (resolve/deliver! result bank))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


;; =============================================================================
;; Set Autopay


(defn- is-bank-id? [source-id]
  (string/starts-with? source-id "ba_"))


(defn set-autopay!
  "Set a source as the autopay source. Source must be a bank account source."
  [{:keys [conn requester stripe] :as ctx} {:keys [id]} _]
  (let [result  (resolve/resolve-promise)
        license (member-license/active (d/db conn) requester)]
    (if-not (is-bank-id? id)
      (resolve/deliver! result nil {:message "Only bank accounts can be used for autopay."})
      (go
        (try
          (let [_         (<!? (autopay/turn-on-autopay! conn stripe license id))
                customer  (customer/autopay (d/db conn) requester)
                source-id (customer/bank-token customer)
                source    (<!? (rcu/fetch-source stripe (customer/id customer) source-id
                                                 :managed-account (member-license/rent-connect-id license)))]
            (resolve/deliver! result source))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


;; =============================================================================
;; Unset Autopay


(defn unset-autopay!
  "Unset a source as the autopay source. Source must be presently used for
  autopay."
  [{:keys [conn requester stripe] :as ctx} {:keys [id]} _]
  (let [result  (resolve/resolve-promise)
        license (member-license/active (d/db conn) requester)]
    (if-not (is-bank-id? id)
      (resolve/deliver! result nil {:message "Only bank accounts can be used for autopay."})
      (go
        (try
          (let [_      (<!? (autopay/turn-off-autopay! conn stripe license id))
                source (<!? (payment-source/fetch-source (d/db conn) stripe requester id))]
            (resolve/deliver! result source))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


;; =============================================================================
;; Set Default Source


(defn set-default!
  "Set a source as the default payment source. The default payment source will
  be used for premium service requests."
  [{:keys [conn requester stripe]} {:keys [id]} _]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [cus-ent  (customer/by-account (d/db conn) requester)
              customer (<!? (rcu/update! stripe (customer/id cus-ent) :default-source id))]
          (resolve/deliver! result (tb/find-by (comp (partial = id) :id) (rcu/sources customer))))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (error-message t)
                                        :err-data (ex-data t)}))))
    result))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :payment.sources/list [_ account params]
  (or (account/admin? account) (= (:db/id account) (:account params))))


(def resolvers
  {;; fields
   :payment.source/autopay?        autopay?
   :payment.source/account         account
   :payment.source/type            type
   :payment.source/name            name
   :payment.source/payments        payments
   :payment.source/default?        default?
   :payment.source/expiration      expiration
   ;; queries
   :payment.sources/list           sources
   ;; mutations
   :payment.sources/delete!        delete!
   :payment.sources/add-source!    add-source!
   :payment.sources/verify-bank!   verify-bank!
   :payment.sources/set-autopay!   set-autopay!
   :payment.sources/unset-autopay! unset-autopay!
   :payment.sources/set-default!   set-default!})
