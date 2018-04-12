(ns odin.graphql.resolvers.payment
  (:require [blueprints.models.account :as account]
            [blueprints.models.order :as order]
            [blueprints.models.payment :as payment]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.service :as service]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [odin.graphql.resolvers.utils :refer [error-message]]
            [taoensso.timbre :as timbre]
            [teller.core :as teller]
            [teller.customer :as tcustomer]
            [teller.payment :as tpayment]
            [teller.property :as tproperty]
            [teller.source :as tsource]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]))

;; ==============================================================================
;; fields =======================================================================
;; ==============================================================================


(defn account
  "The account associated with this `payment`."
  [_ _ payment]
  (tcustomer/account (tpayment/customer payment)))


(defn amount
  "The amount in dollars on this `payment`."
  [_ _ payment]
  (tpayment/amount payment))


(defn autopay?
  "Is this an autopay `payment`?"
  [_ _ payment]
  (and (some? (tpayment/subscription payment))
       (= :payment.type/rent (tpayment/type payment))))


(defn check
  "The check associated with this `payment`, if any."
  [_ _ payment]
  (tpayment/check payment))


;; TODO: Provide this via some sort of public api
(defn created
  "The instant this `payment` was created."
  [{:keys [teller conn]} _ payment]
  (->> payment teller/entity (td/created-at (d/db conn))))


(defn- deposit-desc
  "Description for a security deposit `payment`."
  [account payment]
  (let [entire-deposit-desc  "entire security deposit payment"
        partial-deposit-desc "security deposit installment"
        deposit              (deposit/by-account account)
        entire-py?           (= :deposit.type/full (deposit/type deposit))
        first-py?            (or (= (count (deposit/payments deposit)) 1)
                                 (= (td/id payment)
                                    (->> (deposit/payments deposit)
                                         (map (juxt td/id tpayment/amount))
                                         (sort-by second <)
                                         ffirst)))]
    (cond
      entire-py? entire-deposit-desc
      first-py?  (str "first " partial-deposit-desc)
      :otherwise (str "second " partial-deposit-desc))))


(defn description
  "A description of this `payment`. Varies based on payment type."
  [{:keys [teller conn]} _ payment]
  (letfn [(-rent-desc [payment]
            (->> [(tpayment/period-start payment) (tpayment/period-end payment)]
                 (map date/short)
                 (apply format "rent for %s-%s")))
          (-order-desc [payment]
            ;; NOTE: cheating...kinda
            (let [order        (order/by-payment (d/db conn) (teller/entity payment))
                  service-desc (service/name (order/service order))]
              (or (when-let [d (order/summary order)]
                    (format "%s (%s)" d service-desc))
                  service-desc)))]
    (case (tpayment/type payment)
      :payment.type/rent    (-rent-desc payment)
      :payment.type/order   (-order-desc payment)
      :payment.type/deposit (deposit-desc (tcustomer/account (tpayment/customer payment)) payment)
      nil)))


(defn due
  "The instant this `payment` is due."
  [_ _ payment]
  (tpayment/due payment))


(defn late-fee
  "Any late fees associated with this `payment`."
  [_ _ payment]
  (->> (tpayment/associated payment)
       (filter tpayment/late-fee?)
       (map tpayment/amount)
       (apply +)))


(defn method
  "The method with which this `payment` was made."
  [_ _ payment]
  (when-not (#{:payment.status/due} (tpayment/status payment))
    (let [source (tpayment/source payment)]
      (cond
        (tsource/bank-account? source) :ach
        (tsource/card? source)         :card
        (tpayment/check payment)       :check
        :else                          :other))))


(defn order
  "The order associated with this `payment`, if any."
  [_ _ payment]
  ;; TODO we will discuss how to keep the stripe-id out of the public api
  #_(order/by-subscription-id (tsubscription/stripe-id (tpayment/subscription payment))))


(defn paid-on
  "The instant this `payment` was paid."
  [_ _ payment]
  (tpayment/paid-on payment))


(defn period-end
  "The instant the period of this `payment` ends."
  [_ _ payment]
  (tpayment/period-end payment))


(defn period-start
  "The instant the period of this `payment` ends."
  [_ _ payment]
  (tpayment/period-start payment))


(defn property
  "The property associated with the account that made this `payment`, if any."
  [_ _ payment]
  (tproperty/community (tpayment/property payment)))


(defn source
  "The payment source used to make this `payment`, if any."
  [_ _ payment]
  (tpayment/source payment))


(defn status
  "The status of this `payment`."
  [_ _ payment]
  (keyword (name (tpayment/status payment))))


(defn payment-type
  "What is this `payment` for?"
  [_ _ payment]
  (-> (tpayment/type payment)
      name
      (string/replace "-" "_")
      keyword))


(comment

  (do
    (require '[com.walmartlabs.lacinia :refer [execute]])
    (require '[odin.datomic :refer [conn]])
    (require '[odin.teller :refer [teller]])
    (require '[datomic.api :as d])
    (require '[venia.core :as venia]))


  (let [customer (tcustomer/by-email teller "member@test.com")]
    (tpayment/query teller {:customers [customer]}))

  (clojure.pprint/pprint
   (let [account-id (:db/id (d/entity (d/db conn) [:account/email "member@test.com"]))]
     (execute odin.graphql/schema
              (venia/graphql-query
               {:venia/queries
                [[:payments {:params {:account account-id}}
                  [:id :amount :type :due :pstart :pend
                   [:account [:id]]]]]})
              nil
              {:conn      conn
               :requester (d/entity (d/db conn) [:account/email "admin@test.com"])
               :teller    teller})))

  )

;; =============================================================================
;; Queries
;; =============================================================================


(s/def :gql/account integer?)
(s/def :gql/property string?)
(s/def :gql/source string?)
(s/def :gql/source-types vector?)
(s/def :gql/types vector?)
(s/def :gql/from inst?)
(s/def :gql/to inst?)
(s/def :gql/statuses vector?)
(s/def :gql/currencies vector?)
(s/def :gql/datekey keyword?)


(defn- parse-gql-params
  [{:keys [teller] :as ctx}
   {:keys [account property source source_types types
           from to statuses currencies datekey] :as params}]
  (tb/assoc-when
   params
   :customers (when-some [a account]
                [(tcustomer/by-account teller a)])
   :properties (when-some [p property]
                 [(tproperty/by-community teller p)])
   :sources (when-some [s source]
              [(tsource/by-id teller s)])
   :source-types (when-some [xs source_types]
                   (map #(keyword "payment-source.type" (name %)) xs))
   :types (when-some [xs types]
            (map #(keyword "payment.type" (name %)) xs))
   :statuses (when-some [xs statuses]
               (map #(keyword "payment.status" (name %)) xs))
   :currency (when-let [c (first currencies)]
               (name c))))


(s/fdef parse-gql-params
        :args (s/cat :ctx map?
                     :params (s/keys :opt-un [:gql/account :gql/property :gql/source :gql/source-types :gql/types
                                              :gql/from :gql/to :gql/statuses :gql/currencies :gql/datekey]))
        :ret :teller.payment/query-params)


;; =============================================================================
;; Query


(defn payments
  "Query payments based on `params`."
  [{:keys [teller] :as ctx} {params :params} _]
  (let [tparams (parse-gql-params ctx params)]
    (clojure.pprint/pprint tparams)
    (try
      (tpayment/query teller tparams)
      (catch Throwable t
        (timbre/error t ::query params)
        (resolve/resolve-as nil {:message  (error-message t)
                                 :err-data (ex-data t)})))))


(comment

  (do
    (require '[com.walmartlabs.lacinia :refer [execute]])
    (require '[odin.datomic :refer [conn]])
    (require '[odin.teller :refer [teller]])
    (require '[datomic.api :as d])
    (require '[venia.core :as venia]))

  (let [customer (tcustomer/by-email teller "member@test.com")
        sources  (tcustomer/sources customer)]
    (println (tpayment/query teller {:customers [customer]}))
    ;; TODO why do no sources return?
    (println sources)
    (println (tpayment/query teller {:sources sources})))


  (clojure.pprint/pprint
   (let [account-id  (:db/id (d/entity (d/db conn) [:account/email "member@test.com"]))
         property-id (:db/id (d/entity (d/db conn) [:property/code "52gilbert"]))]
     (execute odin.graphql/schema
              (venia/graphql-query
               {:venia/queries
                [[:payments {:params {:account account-id
                                      :types   [:rent]}}
                  [:id :amount :type :due :pstart :pend
                   [:account [:id]]
                   [:source [:type]]]]]})
              nil
              {:conn      conn
               :requester (d/entity (d/db conn) [:account/email "admin@test.com"])
               :teller    teller})))

  )

;; ==============================================================================
;; mutations ====================================================================
;; ==============================================================================


;; helpers =====================================================================


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


(defn- ensure-payment-allowed
  [payment source]
  (let [retry        (#{:payment.status/due :payment.status/failed}
                      (tpayment/status payment))
        correct-type (#{:payment.type/rent :payment.type/deposit}
                      (tpayment/type payment))
        bank         (tsource/bank-account? source)]
    (cond
      (not retry)
      (format "This payment has status %s; cannot pay!" (name (tpayment/status payment)))

      (not (and correct-type bank))
      "Only bank accounts can be used to pay rent.")))


;; create =======================================================================


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
  [{:keys [requester teller] :as ctx} {:keys [id source] :as params} _]
  (let [payment (tpayment/by-id teller id)
        source  (tsource/by-id teller source)]
    (try
      (if-let [error (ensure-payment-allowed payment source)]
        (resolve/resolve-as nil {:message error})
        (tpayment/charge! payment {:source source}))
      (catch Throwable t
        (timbre/error t ::pay-rent {:payment-id id :source-id source})
        (resolve/resolve-as nil {:message (error-message t)})))))



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
  [{teller :teller} account params]
  (let [payment  (tpayment/by-id teller (:id params))
        customer (tcustomer/by-account teller account)]
    (= customer (tpayment/customer payment))))


(defmethod authorization/authorized? :payment/pay-deposit!
  [{teller :teller} account params]
  (let [payment  (tpayment/by-id teller (:id params))
        customer (tcustomer/by-account teller account)]
    (= customer (tpayment/customer payment))))


(def resolvers
  {;; fields
   :payment/id           (fn [_ _ payment] (tpayment/id payment))
   :payment/account      account
   :payment/amount       amount
   :payment/autopay?     autopay?
   :payment/check        check
   :payment/created      created
   :payment/description  description
   :payment/due          due
   :payment/entity-id    (fn [_ _ payment] (td/id payment))
   :payment/late-fee     late-fee
   :payment/method       method
   :payment/order        order
   :payment/paid-on      paid-on
   :payment/pend         period-end
   :payment/pstart       period-start
   :payment/property     property
   :payment/source       source
   :payment/status       status
   :payment/type         payment-type
   ;; queries
   :payment/list         payments
   ;; mutations
   :payment/create!      create-payment!
   :payment/pay-rent!    pay-rent!
   :payment/pay-deposit! pay-deposit!
   })
