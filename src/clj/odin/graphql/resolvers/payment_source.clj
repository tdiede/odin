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
            [toolbelt.predicates :as p]))

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


(defn- autopay-source
  "Fetch the autopay source for the requesting user, if there is one."
  [{:keys [stripe requester conn]}]
  (when-let [customer (customer/autopay (d/db conn) requester)]
    (when-some [source-id (customer/bank-token customer)]
      (let [managed (property/rent-connect-id (customer/managing-property customer))]
        (rcu/fetch-source stripe (customer/id customer) source-id
                          :managed-account managed)))))

(s/fdef autopay-source
        :args (s/cat :ctx context?)
        :ret (s/or :chan p/chan? :nothing nil?))


(defn- fetch-source
  "Fetch the source by fetching the `requester`'s customer entity and attempting
  to fetch the source present on it. This mandates that the `source-id` actually
  belong to the requesting account."
  [{:keys [requester stripe conn] :as ctx} source-id]
  (let [customer (customer/by-account (d/db conn) requester)]
    (rcu/fetch-source stripe (customer/id customer) source-id)))

(s/fdef fetch-source
        :args (s/cat :ctx context? :source-id string?)
        :ret p/chan?)


(defn- is-autopay-source?
  "Is `source` used as the autopay source?"
  [ctx source]
  (go-try
   (if-let [c (autopay-source ctx)]
     (let [autopay-source (<!? c)]
       [(= (:fingerprint source) (:fingerprint autopay-source)) autopay-source])
     [false nil])))

(s/fdef is-autopay-source?
        :args (s/cat :ctx context? :source map?)
        :ret p/chan?)


;; =============================================================================
;; Fields
;; =============================================================================


(defn autopay?
  "Is this source being used for autopay?"
  [ctx _ source]
  (let [result (resolve/resolve-promise)]
    (go
      (try
        (let [[is-source _] (<!? (is-autopay-source? ctx source))]
          (resolve/deliver! result is-source))
        (catch Throwable t
          ;; (resolve/deliver! result nil))))
          (resolve/deliver! result nil {:message (error-message t)
                                        :err-data (ex-data t)}))))
    result))


(defn default?
  "Is this source the default source?"
  [{:keys [conn requester stripe]} _ source]
  (let [result (resolve/resolve-promise)]
    (if-let [customer (::customer source)]
      (resolve/deliver! result (= (:id source) (:default_source customer)))
      (go
        (try
          (let [cus-ent  (customer/by-account (d/db conn) requester)
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


(defn- sources-by-account
  "Produce all payment sources for a given `account`."
  [{:keys [conn stripe]} account]
  (let [customer (customer/by-account (d/db conn) account)
        result   (resolve/resolve-promise)]
    (if (nil? customer)
      (resolve/deliver! result [])
      (go
        (try
          (let [customer' (<!? (rcu/fetch stripe (customer/id customer)))
                sources   (->> (rcu/sources customer')
                               ;; inject the customer for field resolvers
                               (map #(assoc % ::customer customer')))]
            (resolve/deliver! result sources))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
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
  [ctx {id :id} _]
  (let [result (resolve/resolve-promise)]
    (go
      (let [source (<! (fetch-source ctx id))]
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
  [{:keys [stripe] :as ctx} {:keys [id deposits]} _]
  (let [result (resolve/resolve-promise)]
    (if-not (deposits-valid? deposits)
      (resolve/deliver! result nil {:message  "Please provide valid deposit amounts."
                                    :deposits deposits})
      (go
        (try
          (let [[dep1 dep2]     deposits
                {sid :id
                 cid :customer} (<!? (fetch-source ctx id))
                bank            (<!? (rcu/verify-bank-account! stripe cid sid dep1 dep2))]
            (resolve/deliver! result bank))
          (catch Throwable t
            (resolve/deliver! result nil {:message  (error-message t)
                                          :err-data (ex-data t)})))))
    result))


;; =============================================================================
;; Set Autopay


(defn- create-autopay-customer!
  "Create the autopay customer with `source-id` as a payment source."
  [{:keys [conn stripe requester]} source-id]
  (let [license (member-license/active (d/db conn) requester)
        managed (member-license/rent-connect-id license)]
    (rcu/create2! stripe (account/email requester)
                  :description "autopay"
                  :managed-account managed
                  :source source-id)))


(defn- setup-autopay-customer!
  "Create an autopay customer if necessary, and attach the `source-id` to the
  autopay customer."
  [{:keys [conn requester stripe] :as ctx} license source-id]
  (go-try
   (let [managed      (member-license/rent-connect-id license)
         [ap-cus cus] ((juxt customer/autopay customer/by-account) (d/db conn) requester)
         {token :id}  (<!? (rcn/create-bank-token! stripe (customer/id cus) source-id managed))]
     ;; if there's not an autopay customer...
     (if (nil? ap-cus)
       ;; create the autopay customer and attach the token to it
       (let [customer     (<!? (create-autopay-customer! ctx token))
             bank-account (rcu/active-bank-account customer)]
         ;; create in db
         @(d/transact-async conn [(customer/create (:id customer) requester
                                                   :bank-token (:id bank-account)
                                                   :managing-property (member-license/property license))])
         ;; produce bank account
         bank-account)
       (let [source (<!? (rcu/add-source! stripe (customer/id ap-cus) token :managed-account managed))]
         @(d/transact-async conn [(customer/add-bank-token ap-cus (:id source))])
         source)))))


(defn- plan-name [account property]
  (str (account/full-name account) "'s rent at " (property/name property)))


(defn- plan-amount [member-license]
  (int (* 100 (member-license/rate member-license))))


(defn- setup-autopay-plan!
  "Fetches the autopay plan, or creates it in the event that it doesn't yet
  exist."
  [{:keys [stripe requester]} license]
  (go-try
   (let [plan-id (or (member-license/plan-id license) (str (:db/id license)))
         managed (member-license/rent-connect-id license)]
     (try
       (<!? (rp/fetch stripe plan-id :managed-account managed))
       ;; results in error if no plan exists...
       (catch Throwable _
         ;; so create one
         (<!? (rp/create! stripe plan-id
                          (plan-name requester (member-license/property license))
                          (plan-amount license)
                          :month
                          :descriptor "STARCITY RENT"
                          :managed-account managed)))))))


(defn- is-first-day-of-month? [date]
  (= (t/day (c/to-date-time date)) 1))

(s/fdef is-first-day-of-month?
        :args (s/cat :date inst?)
        :ret boolean?)


(defn- is-past? [date]
  (t/before? (c/to-date-time date) (t/now)))


(defn- first-day-next-month [date tz]
  (date/beginning-of-month (c/to-date (t/plus (c/to-date-time date) (t/months 1))) tz))


(defn- subscription-start-date
  [license]
  (let [commencement (member-license/commencement license)
        tz           (member-license/time-zone license)]
    (cond
      ;; The commencement date already passed, so the subscription needs to
      ;; start in the next calendar month. It's assumend that rent up until that
      ;; point is being collected with some other means.
      (is-past? commencement)               (first-day-next-month (java.util.Date.) tz)
      ;; If `commencement` is not in the past and is already on the first
      ;; day of the month, `commencement` is the plan start date.
      (is-first-day-of-month? commencement) commencement
      ;; Otherwise, it's the first day of the month following commencement.
      :otherwise                            (first-day-next-month commencement tz))))


(defn- update-subscription!
  [{stripe :stripe} license subs-id source-id]
  (rs/update! stripe subs-id
              :source source-id
              :managed-account (member-license/rent-connect-id license)))


(defn- create-subscription!
  [{stripe :stripe} license customer-id plan-id source-id]
  (rs/create! stripe customer-id plan-id
              :source source-id
              :managed-account (member-license/rent-connect-id license)
              :trial-end (c/to-epoch (subscription-start-date license))
              :fee-percent (-> license member-license/property property/ops-fee)))


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
          (let [source (<!? (setup-autopay-customer! ctx license id))]
            (if-let [subs-id (member-license/subscription-id license)]
              ;; update existing subscription with new source
              (<!? (update-subscription! ctx license subs-id (:id source)))
              ;; create subscription
              (let [plan (<!? (setup-autopay-plan! ctx license))
                    subs (<!? (create-subscription! ctx license (:customer source) (:id plan) (:id source)))]
                @(d/transact-async conn [[:db/add (:db/id license) :member-license/subscription-id (:id subs)]
                                         [:db/add (:db/id license) :member-license/plan-id (:id plan)]])))
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
  (let [result (resolve/resolve-promise)]
    (if-not (is-bank-id? id)
      (resolve/deliver! result nil {:message "Only bank accounts can be used for autopay."})
      (go
        (try
          (let [license               (member-license/active (d/db conn) requester)
                subs-id               (member-license/subscription-id license)
                source                (<!? (fetch-source ctx id))
                [is-source ap-source] (<!? (is-autopay-source? ctx source))]
            ;; Remove the source from the customer
            (if-not is-source
              (resolve/deliver! result nil {:message "This source is not being used for autopay; cannot unset."})
              (do
                ;; delete the source from the managed account
                (<!? (rcu/delete-source! stripe (:customer ap-source) (:id ap-source)
                                         :managed-account (member-license/rent-connect-id license)))
                (<!? (rs/cancel! stripe subs-id
                                 :managed-account (member-license/rent-connect-id license)))
                @(d/transact-async conn [[:db/retract (:db/id license) :member-license/subscription-id subs-id]
                                         [:db/retract [:stripe-customer/customer-id (:customer ap-source)]
                                          :stripe-customer/bank-account-token (:id ap-source)]])
                (resolve/deliver! result source))))
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



(comment


  (let [conn odin.datomic/conn
        account (d/entity (d/db conn) [:account/email "member@test.com"])]
    @(d/transact conn [[:db/retract 285873023223104 :stripe-customer/bank-account-token "ba_1Awfc7JDow24Tc1abTE5iR6q"]]))


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
