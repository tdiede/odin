(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [blueprints.models.property :as property]
            [clojure.core.async :as async :refer [<! go]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [odin.graphql.resolvers.payment :as payment-resolvers]
            [odin.graphql.resolvers.utils :refer [context?]]
            [odin.models.autopay :as autopay]
            [odin.models.payment-source :as payment-source]
            [ribbon.customer :as rcu]
            [taoensso.timbre :as timbre]
            [teller.customer :as tcustomer]
            [teller.source :as tsource]
            [toolbelt.async :as ta :refer [<!? go-try]]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn- bank-account? [source]
  (some? (#{"bank_account"} (:object source))))


(defn- error-message [t]
  (or (:message (ex-data t)) (.getMessage t)))

(s/fdef error-message
        :args (s/cat :throwable tb/throwable?)
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


(defn id
  [_ _ source]
  (tsource/id source))


(defn account
  "The account that owns this payment source."
  [_ _ source]
  (tcustomer/account (tsource/customer source)))


(defn last4
  "The last four digits of the source's account/card number."
  [_ _ source]
  (tsource/last4 source))


(defn default?
  "Is this source the default source for premium service orders?"
  [_ _ source]
  (some? (:payment.type/order (tsource/payment-types source))))


(defn expiration
  "Returns the expiration date for a credit card. Returns nil if bank."
  [_ _ source]
  (when (tsource/card? source)
    (str (tsource/exp-month source) "/" (tsource/exp-year source))))


(defn customer
  "Returns the customer for a source."
  [_ _ source]
  "some string")


(defn status
  "The status of source."
  [_ _ source]
  (when (tsource/bank-account? source)
    (tsource/status source)))


(defn autopay?
  "Is this source being used for autopay?"
  [_ _ source]
  ;; TODO return once subscriptions API is complete
  false)


(defn type
  "The type of source, #{:bank :card}."
  [_ _ source]
  (keyword (clojure.core/name (tsource/type source))))


(defn name
  "The name of this source."
  [_ _ source]
  (if (tsource/card? source)
    (tsource/brand source)
    (tsource/bank-name source)))


(defn payments
  "Payments associated with this `source`."
  [_ _ source]
  [])


;; =============================================================================
;; Queries
;; =============================================================================


(defn sources
  "Retrieve payment sources."
  [{:keys [teller]} {:keys [account]} _]
  (when-let [customer (tcustomer/by-account teller account)]
    (tcustomer/sources customer)))


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
        :ret ta/chan?)


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
        (if (tb/throwable? source)
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
  [teller account]
  (if-let [customer (tcustomer/by-account teller account)]
    customer
    (tcustomer/create! teller (account/email account) {:account account})))


;; NOTE: The `requester` in `ctx` is the *account* entity that is making the
;; request
(defn add-source!
  "Add a new source to the requester's Stripe customer, or create the customer
  and add the source if it doesn't already exist."
  [{:keys [teller requester] :as ctx} {:keys [token]} _]
  (let [customer (fetch-or-create-customer! teller requester)]
    (tsource/add-source! customer token)))


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
   :payment-source/id              id
   :payment-source/account         account
   :payment-source/last4           last4
   :payment-source/default?        default?
   :payment-source/expiration      expiration
   :payment-source/customer        customer
   :payment-source/status          status
   :payment-source/autopay?        autopay?
   :payment-source/type            type
   :payment-source/name            name
   :payment-source/payments        payments
   ;; queri-s
   :payment.sources/list           sources
   ;; mutations
   :payment.sources/delete!        delete!
   :payment.sources/add-source!    add-source!
   :payment.sources/verify-bank!   verify-bank!
   :payment.sources/set-autopay!   set-autopay!
   :payment.sources/unset-autopay! unset-autopay!
   :payment.sources/set-default!   set-default!})
