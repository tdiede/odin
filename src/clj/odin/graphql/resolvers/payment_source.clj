(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.account :as account]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [teller.customer :as tcustomer]
            [teller.source :as tsource]
            [teller.spec :as tspec]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- error-message [t]
  (or (:message (ex-data t)) (.getMessage t)))

(s/fdef error-message
        :args (s/cat :throwable tb/throwable?)
        :ret string?)


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
  (tsource/default? source))


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

(comment



  )

;; =============================================================================
;; Mutations
;; =============================================================================


;; =============================================================================
;; Delete Source


(defn delete!
  "Delete the payment source with `id`.

  If the source is a bank account, will
  also delete it on the connected account."
  [{:keys [teller] :as ctx} {id :id} _]
  #_(tsource/delete! (tsource/by-id teller id)))
;; TODO make sure teller does all this for bank, card, connected, platform


;; =============================================================================
;; Close Source


(defn close!
  "Close the payment source with `id`."
  [{:keys [teller] :as ctx} {id :id} _]
  #_(tsource/close! (tsource/by-id teller id)))
;; TODO make sure this is correct implement this


;; =============================================================================
;; Add Source


(defn- fetch-or-create-customer!
  "Produce the customer for `requester` if there is one; otherwise, createa a
  new customer."
  [teller account]
  (if-let [customer (tcustomer/by-account teller account)]
    customer
    (tcustomer/create! teller (account/email account) {:account account})))


;; NOTE: The `requester` in `ctx` is the *account* entity making the request.
(defn add-source!
  "Add a new source to the requester's Stripe customer, or create the customer
  and add the source if it doesn't already exist."
  [{:keys [teller requester] :as ctx} {:keys [token]} _]
  (let [customer (fetch-or-create-customer! teller requester)]
    (tsource/add-source! customer token)))


;; =============================================================================
;; Verify Bank Account


(s/def ::deposit
  (s/and pos-int? (partial > 100)))

(defn- deposit? [x]
  (s/valid? ::deposit x))

(s/def ::deposits
  (s/coll-of deposit? :kind vector? :count 2))

(defn- deposits? [deposits]
  (s/valid? ::deposits deposits))


(defn verify-bank!
  "Verify a bank account given the two microdeposit amounts that were made to
  the bank account."
  [{:keys [teller requester] :as ctx} {:keys [id deposits]} _]
  (if-not (deposits? deposits)
    (resolve/resolve-as nil {:message  "Please provide valid deposit amounts."
                             :deposits deposits})
    (let [source (tsource/by-id teller id)]
      (try
        (tsource/verify-bank-account! source deposits)
        (catch Throwable t
          (timbre/error t ::verify-bank-account {:email (account/email requester)
                                                 :id    id})
          (resolve/resolve-as nil {:message (error-message t)}))))))


;; =============================================================================
;; Set Autopay


(defn set-autopay!
  "Set a source as the autopay source. Source must be a bank account source."
  [{:keys [teller requester] :as ctx} {:keys [id]} _]
  ;; TODO
  )


;; =============================================================================
;; Unset Autopay


(defn unset-autopay!
  "Unset a source as the autopay source. Source must be presently used for
  autopay."
  [{:keys [teller requester] :as ctx} {:keys [id]} _]
  ;; TODO
  )


;; =============================================================================
;; Set Default Source


(defn set-default!
  "Set a source as the default payment source. The default payment source will
  be used for premium service requests."
  [{:keys [teller]} {:keys [id]} _]
  (tsource/set-default! (tsource/by-id teller id) :payment.type/order))


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
   ;; queries
   :payment.sources/list           sources
   ;; mutations
   :payment.sources/delete!        delete!
   :payment.sources/close!         close!
   :payment.sources/add-source!    add-source!
   :payment.sources/set-default!   set-default!
   :payment.sources/verify-bank!   verify-bank!
   :payment.sources/set-autopay!   set-autopay!
   :payment.sources/unset-autopay! unset-autopay!})
