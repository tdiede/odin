(ns odin.graphql.resolvers.payment
  (:require [blueprints.models.payment :as payment]
            [clojure.core.async :as async :refer [<! chan go]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [ribbon.charge :as rch]
            [ribbon.invoice :as ri]
            [toolbelt.async :refer [<!!? <!?]]
            [toolbelt.predicates :as p]
            [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]))

;;; NOTE: Think about error handling. At present, errors will propogate at the
;;; field level, not the top level. This means that if a single request from
;;; Stripe fails, we'll get back partial results. This seems desirable.


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- get-charge [payment]
  (let [v (get payment ::charge)]
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
  [context _ payment]
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


(defn- inject-charge [ctx payment charge-id]
  (go (assoc payment ::charge (<! (rch/fetch (:stripe ctx) charge-id)))))


(defn- inject-invoice
  "The only time we need to pull from a managed account is if we're looking at
  an invoice where the payment is for rent."
  [{:keys [db stripe]} payment invoice-id]
  (go
    ;; This is ugly and repetitive...
    (if (payment/autopay? payment)
      (let [managed-account (->> (payment/account payment)
                                 (member-license/active db)
                                 (member-license/rent-connect-id))]
        (let [invoice (<! (ri/fetch stripe invoice-id
                                    :managed-account managed-account))]
          (assoc payment ::charge (<! (rch/fetch stripe (:charge invoice)
                                                 :managed-account managed-account)))))
      (let [invoice (<! (ri/fetch stripe invoice-id))]
        (assoc payment ::charge (<! (rch/fetch stripe (:charge invoice))))))))


(defn- inject-stripe-data [ctx payments]
  (map
   (fn [{:keys [stripe/charge-id stripe/invoice-id] :as payment}]
     (cond
       (some? charge-id)  (inject-charge ctx payment charge-id)
       (some? invoice-id) (inject-invoice ctx payment invoice-id)
       :otherwise         (let [c (chan 1)]
                            (async/put! c payment)
                            (async/close! c)
                            c)))
   payments))


(defn- query-payments [db account]
  (->> (payment/payments db account)
       (sort-by :payment/paid-on)
       (map
        (fn [payment]
          (assoc (into {} payment) :db/id (:db/id payment))))))


(defn payments
  "Asynchronously fetch payments for `account_id` or the requesting user, if
  `account_id` is not supplied."
  [{:keys [stripe db] :as ctx} {:keys [account]} _]
  (let [account (d/entity db account)
        result  (resolve/resolve-promise)]
    (go
      (try
        (let [payments (->> (query-payments db account)
                            (inject-stripe-data ctx)
                            (async/merge))]
          ;; TODO: fix sort order!
          (resolve/deliver! result (->> (<!? (async/into [] payments))
                                        (sort-by :payment/paid-on))))
        (catch Throwable t
          (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
                                        :err-data (ex-data t)}))))
    result))


(comment

  (let [conn    odin.datomic/conn
        context {:db        (d/db conn)
                 :conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester (d/entity (d/db conn) [:account/email "member@test.com"])}
        ]
    (map :payment/paid-on (query-payments (:db context) (:requester context))))




  )
