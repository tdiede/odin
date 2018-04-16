(ns odin.routes.onboarding
  (:require [blueprints.models.account :as account]
            [blueprints.models.approval :as approval]
            [blueprints.models.catalogue :as catalogue]
            [blueprints.models.customer :as customer]
            [blueprints.models.events :as events]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.news :as news]
            [blueprints.models.onboard :as onboard]
            [blueprints.models.order :as order]
            [blueprints.models.payment :as payment]
            [blueprints.models.promote :as promote]
            [blueprints.models.property :as property]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.service :as service]
            [blueprints.models.unit :as unit]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [compojure.core :refer [defroutes GET POST]]
            [datomic.api :as d]
            [ribbon.charge :as rc]
            [ribbon.customer :as rcu]
            [ring.util.response :as resp]
            [odin.config :as config :refer [config]]
            [odin.datomic :refer [conn]]
            [odin.teller :refer [teller]]
            [odin.routes.util :refer :all]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!!?]]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]
            [teller.customer :as tcustomer]
            [teller.property :as tproperty]))


;; ==============================================================================
;; util =========================================================================
;; ==============================================================================


;; request ======================================================================


(defn requester
  "Produce the `account` entity that initiated this `request`."
  [db request]
  (let [id (get-in request [:identity :db/id])]
    (d/entity db id)))

(s/fdef requester
        :args (s/cat :db td/db? :request map?)
        :ret (s/or :nothing nil? :entity td/entity?))

;; response =====================================================================


(defn malformed
  "Given a response `body`, produce a resposne with status code 400."
  [body]
  (-> (resp/response body)
      (resp/status 400)))

(defn unprocessable
  "Given a response `body`, produce a resposne with status code 422."
  [body]
  (-> (resp/response body)
      (resp/status 422)))

(defn ok
  "Given a response `body`, produce a resposne with status code 200."
  [body]
  (resp/response body))

(defn forbidden
  "Given a response `body`, produce a resposne with status code 403."
  [body]
  (-> (resp/response body)
      (resp/status 403)))

(defn json [response]
  (resp/content-type response "application/json; charset=utf-8"))

(defn transit [response]
  (resp/content-type response "application/transit+json"))

(def transit-malformed (comp transit malformed))
(def json-malformed (comp json malformed))

(def transit-unprocessable (comp transit unprocessable))
(def json-unprocessable (comp json unprocessable))

(def json-ok (comp json ok))
(def transit-ok (comp transit ok))

(def transit-forbidden (comp transit forbidden))


;; validation ===================================================================


(defn- extract-errors
  [errors-map acc]
  (reduce
   (fn [acc [k v]]
     (cond
       (sequential? v) (concat acc v)
       (map? v)        (extract-errors v acc)
       :otherwise      (throw (ex-info (str "Unexpected errors format! Expected sequential or map, got " (type v))
                                       {:offending-value v :key k}))))
   acc
   errors-map))

(defn errors
  "Extract errors from a bouncer error map."
  [[errors _]]
  (extract-errors errors []))

(defn valid?
  ([vresult]
   (valid? vresult identity))
  ([[errors result] tf]
   (if (nil? errors)
     (tf result)
     false)))

(def not-valid? (comp not valid?))

(defn result [vresult]
  (second vresult))

(v/defvalidator float*
  {:default-message-format "%s must be a floating-point number"}
  [maybe-float]
  (float? maybe-float))

(v/defvalidator inst
  {:default-message-format "%s must be an instant"}
  [maybe-inst]
  (inst? maybe-inst))


;; ==============================================================================
;; steps ========================================================================
;; ==============================================================================


;; NOTE: Do we want to deal with dependencies like we do on the client? This may
;; make sense, since certain steps are moot in the absense of satisfied
;; prerequisites.
(def steps
  [:admin/emergency
   :services/moving
   :services/storage
   :services/customize
   :services/cleaning
   :services/upgrades
   :deposit/method
   :deposit.method/bank
   :deposit.method/verify
   :deposit/pay])

(s/def ::step (set steps))


;; =============================================================================
;; validate
;; =============================================================================


(defmulti validations
  "Produce the validations for `step`."
  (fn [conn account step] step))


(defmethod validations :default [_ _ _] {})


(defmethod validations :admin/emergency
  [_ _ _]
  {:first-name   [[v/required :message "Please provide your contact's first name."]]
   :last-name    [[v/required :message "Please provide your contact's last name."]]
   :phone-number [[v/required :message "Please provide your contact's phone number."]]})


(defmethod validations :deposit/method
  [_ _ _]
  {:method [[v/required :message "Please choose a payment method."]
            [v/member #{"ach" "check"} :message "Please choose a valid payment method."]]})


(defmethod validations :deposit.method/bank
  [_ _ _]
  {:stripe-token [[v/required :message "Something went wrong; please try again or contact support."]]})


(defmethod validations :deposit.method/verify
  [_ _ _]
  (let [integer  [v/integer :message "Please enter your deposits in cents (whole numbers only)."]
        in-range [v/in-range [1 100] :message "Please enter a number between one and 100."]]
    {:amount-1 [[v/required :message "Please provide the first deposit amount."]
                integer in-range]
     :amount-2 [[v/required :message "Please provide the second deposit amount."]
                integer in-range]}))


(defmethod validations :deposit/pay
  [_ _ _]
  {:method [[v/required :message "Please choose a payment option."]
            [v/member #{"partial" "full"} :message "Please choose a valid payment option."]]})


;; If `needed` is false, no other reqs
(defmethod validations :services/moving
  [_ account _]
  (letfn [(-commencement [account]
            (-> account approval/by-account approval/move-in date/beginning-of-day))
          (-after-commencement? [date]
            (or (= date (-commencement account))
                (t/after? (c/to-date-time date) (c/to-date-time (-commencement account)))))]
    {:needed [[v/required :message "Please indicate whether or not you need moving assistance."]]
     :date   [[v/required :message "Please provide a move-in date." :pre (comp true? :needed)]
              [-after-commencement? :message "Your move-in date cannot be before your license commences." :pre (comp true? :needed)]]
     :time   [[v/required :message "Please provide a move-in time." :pre (comp true? :needed)]]}))

;; NOTE: Skipping validations on catalogue services atm
;; TODO: Validations on catalogue services

(defn validate
  "Produces `nil` when `data` is valid for `step`, and a vector of error
  messages otherwise."
  [conn account step data]
  (when-let [vresult (b/validate data (validations conn account step))]
    (when-not (valid? vresult)
      (errors vresult))))


;; =============================================================================
;; fetch
;; =============================================================================


(s/def ::complete boolean?)
(s/def ::data map?)


;;; Complete


(defmulti complete?
  "Has `account` completed `step`?"
  (fn [db account step] step))

(s/fdef complete?
        :args (s/cat :db td/db?
                     :account td/entity?
                     :step ::step)
        :ret (s/or :bool boolean? :nothing nil?))


(defmethod complete? :default [_ _ _] false)


(defmethod complete? :admin/emergency [_ account _]
  (let [contact (:account/emergency-contact account)]
    (boolean
     (and (:person/first-name contact)
          (:person/last-name contact)
          (:person/phone-number contact)))))


(defmethod complete? :deposit/method
  [_ account _]
  (let [deposit (deposit/by-account account)]
    (boolean (deposit/method deposit))))


(defmethod complete? :deposit.method/bank
  [db account _]
  (if (= (-> account deposit/by-account deposit/method)
         :deposit.method/check)
    nil
    (if-let [customer (customer/by-account db account)]
      (let [stripe (config/stripe-secret-key config)
            cus    (<!!? (rcu/fetch stripe (customer/id customer)))]
        (or (some? (rcu/unverified-bank-account cus))
            (rcu/has-verified-bank-account? cus)))
      false)))


(defmethod complete? :deposit.method/verify
  [db account _]
  (let [stripe (config/stripe-secret-key config)]
    (when-let [customer (customer/by-account db account)]
      (rcu/has-verified-bank-account? (<!!? (rcu/fetch stripe (customer/id customer)))))))


(defmethod complete? :deposit/pay
  [_ account _]
  (let [deposit (deposit/by-account account)]
    (deposit/is-paid? deposit)))


(defmethod complete? :services/moving
  [db account _]
  (let [onboard (onboard/by-account account)]
    (or (onboard/seen-moving? onboard)
        (and (inst? (onboard/move-in onboard))
             (let [s (service/moving-assistance (d/db conn))]
               (order/exists? db account s))))))


(defmethod complete? :services/storage
  [_ account _]
  (let [onboard (onboard/by-account account)]
    (onboard/seen-storage? onboard)))


(defmethod complete? :services/customize
  [_ account _]
  (let [onboard (onboard/by-account account)]
    (onboard/seen-customize? onboard)))


(defmethod complete? :services/cleaning
  [_ account _]
  (let [onboard (onboard/by-account account)]
    (onboard/seen-cleaning? onboard)))


(defmethod complete? :services/upgrades
  [_ account _]
  (let [onboard (onboard/by-account account)]
    (onboard/seen-upgrades? onboard)))


;; =====================================
;; Fetch


(defn- order-params
  "Produce the client-side parameters for services ordered by `account` from
  `catalogue`."
  [db account catalogue]
  (->> (service/ordered-from-catalogue db account catalogue)
       (d/q '[:find ?o ?s
              :in $ ?a [?s ...]
              :where
              [?o :order/account ?a]
              [?o :order/service ?s]]
            db (:db/id account))
       (reduce
        (fn [acc [order-id service-id]]
          (let [order (d/entity db order-id)]
            (-> {service-id
                 (tb/assoc-when
                  {}
                  :quantity (order/quantity order)
                  :desc (order/desc order)
                  :variant (:db/id (order/variant order)))}
                (merge acc))))
        {})))


(s/def ::quantity (s/and pos? number?))
(s/def ::desc string?)
(s/def ::variant integer?)
(s/def ::order-params
  (s/map-of integer? (s/keys :opt-un [::quantity ::desc ::variant])))


(s/fdef order-params
        :args (s/cat :db td/db?
                     :account td/entity?
                     :catalogue td/entity?)
        :ret ::order-params)


(defmulti ^:private fdata
  (fn [conn account step] step))

(defn fetch
  "Given a `step`, produce a map containing keys `complete` and `data`, where
  `complete` tells us whether or not this step has been completed, and `data` is
  the information entered by `account` in `step`."
  [conn account step]
  {:complete (complete? (d/db conn) account step)
   :data     (fdata conn account step)})

(s/fdef fetch
        :args (s/cat :conn td/conn?
                     :account td/entity?
                     :step ::step)
        :ret (s/or :response (s/keys :req-un [::complete ::data])
                   :nothing nil?))

(defmethod fdata :default [_ _ _] nil)

(defmethod fdata :admin/emergency [_ account _]
  (let [contact (:account/emergency-contact account)]
    {:first-name   (:person/first-name contact)
     :last-name    (:person/last-name contact)
     :phone-number (:person/phone-number contact)}))

(defmethod fdata :deposit/method
  [conn account step]
  (let [deposit (deposit/by-account account)
        method  (deposit/method deposit)]
    (if method {:method (name method)} {})))

(defmethod fdata :deposit.method/bank
  [conn account step]
  ;; No data required, just completion
  {})

(defmethod fdata :deposit.method/verify
  [conn account step]
  ;; No data required, just completion
  {})

(defmethod fdata :deposit/pay
  [conn account step]
  ;; No data required, just completion
  {})

(defmethod fdata :services/moving
  [conn account step]
  (let [onboard (onboard/by-account account)
        service (service/moving-assistance (d/db conn))
        order   (order/by-account (d/db conn) account service)]
    {:needed (when (onboard/seen-moving? onboard) (td/entity? order))
     :date   (onboard/move-in onboard)
     :time   (onboard/move-in onboard)}))

(defmethod fdata :services/storage
  [conn account step]
  (let [onboard   (onboard/by-account account)
        property  (-> account approval/by-account approval/property)
        catalogue (catalogue/storage (d/db conn) property)]
    {:seen      (onboard/seen-storage? onboard)
     :orders    (order-params (d/db conn) account catalogue)
     :catalogue (catalogue/clientize catalogue)}))

(defmethod fdata :services/customize
  [conn account step]
  (let [onboard   (onboard/by-account account)
        catalogue (catalogue/customization (d/db conn))]
    {:seen      (onboard/seen-customize? onboard)
     :orders    (order-params (d/db conn) account catalogue)
     :catalogue (catalogue/clientize catalogue)}))

(defmethod fdata :services/cleaning
  [_ account _]
  (let [onboard   (onboard/by-account account)
        catalogue (catalogue/cleaning+laundry (d/db conn))]
    {:seen      (onboard/seen-cleaning? onboard)
     :orders    (order-params (d/db conn) account catalogue)
     :catalogue (catalogue/clientize catalogue)}))

(defmethod fdata :services/upgrades
  [_ account _]
  (let [onboard   (onboard/by-account account)
        property  (-> account approval/by-account approval/property)
        catalogue (catalogue/upgrades (d/db conn) property)]
    {:seen      (onboard/seen-upgrades? onboard)
     :orders    (order-params (d/db conn) account catalogue)
     :catalogue (catalogue/clientize catalogue)}))

;; =============================================================================
;; fetch-all
;; =============================================================================

(defn fetch-all
  "This is just `fetch`, but performed on all steps: i.e. a reduction."
  [conn account]
  (reduce
   (fn [acc step]
     (tb/assoc-when acc step (fetch conn account step)))
   {}
   steps))

(s/fdef fetch-all
        :args (s/cat :conn td/conn? :account td/entity?)
        :ret map?)

;; =============================================================================
;; save
;; =============================================================================

(defmulti save!
  "Accepts a `step` and `data`. Persist the data and perform any necessary
  side-effects."
  (fn [conn account step data] step))

(s/fdef save!
        :args (s/cat :conn td/conn?
                     :account td/entity?
                     :step ::step
                     :data ::data))

(defmethod save! :default [conn account step _]
  (timbre/debugf "no `save!` method implemented for %s" step))

;; =============================================================================
;; Administrative


(defmethod save! :admin/emergency
  [conn account _ {:keys [first-name last-name phone-number]}]
  (let [tx {:person/first-name   first-name
            :person/last-name    last-name
            :person/phone-number phone-number}]
    @(d/transact conn
                 [(if-let [contact (:account/emergency-contact account)]
                    (assoc tx :db/id (:db/id contact))
                    {:db/id                     (:db/id account)
                     :account/emergency-contact tx})])))


;; =============================================================================
;; Security Deposit


(defmethod save! :deposit/method
  [conn account _ {method :method}]
  (let [method  (keyword "security-deposit.payment-method" method)
        deposit (deposit/by-account account)]
    @(d/transact conn [{:db/id                   (:db/id deposit)
                        :security-deposit/payment-method method}])))


(defmethod save! :deposit.method/bank
  [conn account _ {token :stripe-token}]
  (if-some [customer (tcustomer/by-account teller account)]
    (tcustomer/add-source! teller customer token)
    (let [cm (account/current-property (d/db conn) account)]
      (tcustomer/create! teller (account/email account)
                         (cond-> {:source token
                                  :account account}
                           (some? cm)
                           (conj {:property (tproperty/by-community teller cm)}))))))


(def ^:private verification-failed-error
  "The maximum number of verification attempts has been exceeded, and verification has failed. Please reload the page and input your bank credentials again.")


(defmethod save! :deposit.method/verify
  [conn account _ {:keys [amount-1 amount-2]}]
  (let [stripe   (config/stripe-secret-key config)
        customer (customer/by-account (d/db conn) account)
        cus      (<!!? (rcu/fetch stripe (customer/id customer)))]
    (if (rcu/verification-failed? cus)
      (let [sid (:id (tb/find-by rcu/failed-bank-account? (rcu/bank-accounts cus)))]
        @(d/transact conn [(events/delete-source (customer/id customer) sid)])
        (throw (ex-info "Verification has failed!" {:message verification-failed-error})))
      (do
        (<!!? (rcu/verify-bank-account! stripe
                                        (customer/id customer)
                                        (:id (rcu/unverified-bank-account cus))
                                        amount-1 amount-2))
        @(d/transact conn [(assoc {:db/id (td/id customer)}
                                  :stripe-customer/bank-account-token (:id (rcu/unverified-bank-account cus)))])))))


(defn- charge-amount
  "Determine the correct amount to charge in cents given "
  [method deposit]
  (if (= "full" method)
    (int (* (deposit/amount deposit) 100))
    50000))

(defn- create-charge
  [db account deposit method]
  (let [customer (customer/by-account db account)]
    (<!!? (rc/create! (config/stripe-secret-key config)
                      (charge-amount method deposit)
                      (customer/bank-token customer)
                      :email (account/email account)
                      :description (format "'%s' security deposit payment" method)
                      :customer-id (customer/id customer)
                      :destination (-> account
                                       account/approval
                                       approval/unit
                                       unit/property
                                       property/deposit-connect-id)))))

(defmethod save! :deposit/pay
  [conn account step {method :method}]
  (if (complete? (d/db conn) account step)
    (throw (ex-info "Cannot charge customer for security deposit twice!"
                    {:account (:db/id account)}))
    (let [deposit   (deposit/by-account account)
          charge-id (:id (create-charge (d/db conn) account deposit method))
          amount    (float (/ (charge-amount method deposit) 100))
          payment   (payment/create amount account
                                    :for :payment.for/deposit
                                    :charge-id charge-id)]
      @(d/transact conn [{:db/id            (:db/id deposit)
                          :deposit/type     (keyword "deposit.type" method)
                          :deposit/payments (td/id payment)}
                         payment
                         (events/deposit-payment-made account charge-id)]))))

(comment

  (let [account (d/entity (d/db conn) [:account/email "onboard@test.com"])]
    (rcu/fetch (config/stripe-secret-key config)
               (:customer/platform-id (customer/by-account (d/db conn) account))))

  )

;; =============================================================================
;; Services

(defn- update-orders
  "Update all orders for services in `params` that are also in `existing`."
  [db account params existing]
  (->> (reduce (fn [acc [k v]] (if (empty? v) acc (conj acc k))) #{} params) ; remove keys w/ null vals
       (set/intersection (set existing))
       ;; find tuples of [order service] given service-ids to update
       (d/q '[:find ?o ?s
              :in $ ?a [?s ...]
              :where
              [?o :order/account ?a]
              [?o :order/service ?s]]
            db (:db/id account))
       ;; gen txes
       (map
        (fn [[order-id service-id]]
          (let [{:keys [quantity desc variant] :as params} (get params service-id)
                order                                      (d/entity db order-id)]
            (->> (tb/assoc-when
                  {}
                  :quantity (when-let [q quantity] (float q))
                  :desc     desc
                  :variant  variant)
                 (order/update order)))))
       (remove empty?)))

(defn- remove-orders
  "Remoe all orders for services in `existing` but not in `params`."
  [db account params existing]
  (->> (set/difference (set existing) (set (keys params))) ; svc ids whose orders should be removed
       ;; find orders for services
       (d/q '[:find [?o ...]
              :in $ ?a [?s ...]
              :where
              [?o :order/account ?a]
              [?o :order/service ?s]]
            db (:db/id account))
       ;; gen txes
       (map (partial conj [:db.fn/retractEntity]))))

(defn- create-orders
  "Create new orders for all services in `params`."
  [db account params existing]
  (->> (set/difference (set (keys params)) (set existing))
       (select-keys params)
       ;; gen txes
       (map
        (fn [[service-id params]]
          (let [service (d/entity db service-id)]
            (order/create account service
                          (tb/assoc-when
                           {}
                           :quantity (when-let [q (:quantity params)] (float q))
                           :desc (:desc params)
                           :variant (:variant params))))))))

(defn orders-tx
  "Given server-side `params` and a `catalogue`, generate a transaction to
  create orders for newly requested services, remove orders that are no longer
  requested, and update any orders that may have changed."
  [db account catalogue params]
  (let [existing (service/ordered-from-catalogue db account catalogue)]
    (->> ((juxt create-orders update-orders remove-orders)
          db account params existing)
         (apply concat))))

(s/fdef orders-tx
        :args (s/cat :db td/db?
                     :account td/entity?
                     :catalogue td/entity?
                     :params ::order-params)
        :ret vector?)

;; =====================================
;; Moving Assistance

(defn- combine [date time]
  (let [date (c/to-date-time date)
        time (c/to-date-time time)]
    (-> (t/date-time (t/year date) (t/month date) (t/day date) (t/hour time) (t/minute time))
        (c/to-date))))

(defn- add-move-in-tx
  [db onboard move-in]
  (let [service (service/moving-assistance db)]
    (tb/conj-when
     [{:db/id           (:db/id onboard)
       :onboard/move-in move-in}]
     ;; When there's not an moving-assistance order, create one.
     (when-not (order/exists? db (onboard/account onboard) service)
       (order/create (onboard/account onboard) service)))))

(defn- remove-move-in-tx
  [db account onboard]
  (let [retract-move-in (when-let [v (onboard/move-in onboard)]
                          [:db/retract (:db/id onboard) :onboard/move-in v])
        retract-order   (order/remove-existing db account (service/moving-assistance db))]
    (tb/conj-when [] retract-move-in retract-order)))

(defmethod save! :services/moving
  [conn account step {:keys [needed date time]}]
  (let [onboard (onboard/by-account account)
        service (service/moving-assistance (d/db conn))
        order   (order/by-account (d/db conn) account service)]
    @(d/transact conn (-> (if needed
                            (add-move-in-tx (d/db conn) onboard (combine date time))
                            (remove-move-in-tx (d/db conn) account onboard))
                          (conj (onboard/add-seen onboard step))))))

;; =====================================

(defmethod save! :services/storage
  [conn account step {seen :seen :as params}]
  (let [onboard   (onboard/by-account account)
        property  (-> account approval/by-account approval/property)
        catalogue (catalogue/storage (d/db conn) property)]
    @(d/transact conn (conj
                       (orders-tx (d/db conn) account catalogue (:orders params))
                       (onboard/add-seen onboard step)))))

(defmethod save! :services/customize
  [conn account step {seen :seen :as params}]
  (let [onboard   (onboard/by-account account)
        catalogue (catalogue/customization (d/db conn))]
    @(d/transact conn (conj
                       (orders-tx (d/db conn) account catalogue (:orders params))
                       (onboard/add-seen onboard step)))))

(defmethod save! :services/cleaning
  [conn account step {seen :seen :as params}]
  (let [onboard   (onboard/by-account account)
        catalogue (catalogue/cleaning+laundry (d/db conn))]
    @(d/transact conn (conj
                       (orders-tx (d/db conn) account catalogue (:orders params))
                       (onboard/add-seen onboard step)))))

(defmethod save! :services/upgrades
  [conn account step {seen :seen :as params}]
  (let [onboard   (onboard/by-account account)
        property  (-> account approval/by-account approval/property)
        catalogue (catalogue/upgrades (d/db conn) property)]
    @(d/transact conn (conj
                       (orders-tx (d/db conn) account catalogue (:orders params))
                       (onboard/add-seen onboard step)))))

;; =============================================================================
;; Error Responses
;; =============================================================================

(defmulti on-error
  "Issue a custom response for `step` when exception `ex` is encountered.
  Rethrows by default."
  (fn [conn account step ex] step))

(defmethod on-error :default [_ _ _ ex]
  (throw ex))

(defmethod on-error :deposit.method/verify [_ _ _ ex]
  (if-let [message (get (ex-data ex) :message)]
    (transit-malformed {:errors [message]})
    (throw ex)))

;; =============================================================================
;; Routes & Handlers
;; =============================================================================

(defn- is-finished? [db account]
  (let [deposit (deposit/by-account account)
        onboard (onboard/by-account account)]
    (and (deposit/is-paid? deposit)
         (complete? db account :admin/emergency)
         (onboard/seen-cleaning? onboard)
         (onboard/seen-customize? onboard)
         (onboard/seen-moving? onboard)
         (onboard/seen-storage? onboard)
         (onboard/seen-upgrades? onboard))))

(defn- finish! [conn account {token :token}]
  (when token
    (if-let [customer (customer/by-account (d/db conn) account)]
      (<!!? (rcu/add-source! (config/stripe-secret-key config)
                             (customer/id customer) token))
      (<!!? (rcu/create! (config/stripe-secret-key config) (account/email account) token))))
  @(d/transact conn (conj (promote/promote account)
                          (events/account-promoted account))))

(defn finish-handler
  [{:keys [params session] :as req}]
  (let [account  (requester (d/db conn) req)
        finished (is-finished? (d/db conn) account)
        orders   (order/orders (d/db conn) account)]
    (cond
      (not finished)
      (transit-unprocessable {:error "Cannot submit; onboarding is unfinished."})

      (some? (member-license/active (d/db conn) account))
      (transit-unprocessable {:error "Your onboarding has been finished; please refresh the page."})


      ;; If there are orders, ensure that a token has been passed along.
      (and (> (count orders) 0) (not (:token params)))
      (transit-malformed {:error "Your credit card details are required."})

      :otherwise (let [session (assoc-in session [:identity :account/role] :account.role/member)]
                   (finish! conn account params)
                   (-> (transit-ok {:message "ok"})
                       (assoc :session session))))))

(defroutes routes
  (GET "/" []
       (fn [req]
         (transit-ok {:result (fetch-all conn (requester (d/db conn) req))})))

  (POST "/" []
        (fn [{:keys [params] :as req}]
          (let [{:keys [step data]} params
                account             (->requester req)]
            (if-let [errors (validate conn account step data)]
              (transit-malformed {:errors errors})
              (try
                (save! (->conn req) account step data)
                (transit-ok {:result (fetch conn (d/entity (d/db conn) (:db/id account)) step)})
                (catch Exception e
                  (on-error conn account step e)))))))

  (POST "/finish" [] finish-handler))

(comment
  (fetch-all conn (account/by-email (d/db conn) "onboarding@test.com"))

  )
