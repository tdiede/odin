(ns migrations.teller.customers
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.property :as property]
            [stripe.token :as token]
            [clojure.string :as string]
            [datomic.api :as d]
            [odin.config :as config :refer [config]]
            [stripe.customer :as scustomer]
            [stripe.http :as h]
            [teller.property :as tproperty]
            [toolbelt.datomic :as td]
            [toolbelt.core :as tb]
            [clojure.set :as set]
            [taoensso.timbre :as timbre]))

(defn non-autopay-customers [db]
  (->> (d/q '[:find [?e ...]
              :in $
              :where
              [?e :stripe-customer/customer-id _]
              [(missing? $ ?e :stripe-customer/managed)]]
            db)
       (map (partial d/entity db))))


;; (defn autopay-customers [db]
;;   (->> (d/q '[:find [?e ...]
;;               :in $
;;               :where
;;               [?e :stripe-customer/customer-id _]
;;               [?e :stripe-customer/managed _]]
;;             db)
;;        (map (partial d/entity db))))


(defn- community-for-account
  [db account]
  (or (account/current-property db account)
      (d/q '[:find ?p .
             :in $ ?a
             :where
             [?a :account/licenses ?ls]
             [?ls :member-license/unit ?u]
             [?p :property/units ?u]
             (or [?ls :member-license/status :member-license.status/canceled]
                 [?ls :member-license/status :member-license.status/inactive])]
           db (td/id account))))


(defn- connected-customer-txdata
  [autopay customer sources]
  (let [ops-id      (-> autopay customer/managing-property :property/rent-connect-id)
        deposit-id  (-> autopay customer/managing-property :property/deposit-connect-id)
        customer-id "FIXME" #_(:id (scustomer/create! {:email (account/email (customer/account autopay))}
                                                      {:account deposit-id}))]
    (doseq [source sources]
      (timbre/infof "\ncreating deposit source! autopay-customer-id: %s\n connect-id: %s\n customer-id: %s\n customer-email: %s\n source-id: %s" customer-id deposit-id (customer/id customer) (account/email (customer/account customer)) (:id source))
      #_(create-connect-source! (customer/id customer)
                                (customer/id autopay)
                                connect-id
                                (:id source)))
    [{:connected-customer/customer-id       (customer/id autopay)
      :connected-customer/connected-account [:connect-account/id ops-id]}
     {:connected-customer/customer-id       customer-id
      :connected-customer/connected-account [:connect-account/id deposit-id]}]))


(defn ->source-type
  [object]
  (get
   {"bank_account" :payment-source.type/bank
    "card"         :payment-source.type/card}
   object))


(defn- construct-fingerprint
  [source]
  (if (= "card" (:object source))
    [(:last4 source)
     (:exp_month source)
     (:exp_year source)]
    [(:last4 source)
     (:bank_name source)]))


(defn- create-source-token!
  [source-id customer-id]
  (let [f (if (string/starts-with? source-id "card")
            token/create-card-token!
            token/create-bank-token!)]
    (:id (f source-id {:params {:customer customer-id}}))))


(defn- create-connect-source!
  [platform-customer-id connect-customer-id account-id source-id]
  (h/with-connect-account account-id
    (let [token (create-source-token! source-id platform-customer-id)]
      (scustomer/add-source! connect-customer-id token))))


(defn- sources-to-propagate
  [platform-sources connect-sources]
  (let [diff (set/difference
              (set (map construct-fingerprint platform-sources))
              (set (map construct-fingerprint connect-sources)))]
    (filterv (comp diff construct-fingerprint) platform-sources)))


(defn- propagate-ops-customer!
  [db customer platform-sources]
  (when-let [autopay (customer/autopay db (customer/account customer))]
    (let [connect-id (-> autopay
                         customer/managing-property
                         :property/rent-connect-id)
          csources   (-> (scustomer/fetch (customer/id autopay)
                                          {:account connect-id})
                         (get-in [:sources :data]))]
      (doseq [source (sources-to-propagate platform-sources csources)]
        (timbre/infof "\ncreating ops source! autopay-customer-id: %s\n connect-id: %s\n customer-id: %s\n customer-email: %s\n source-id: %s" (customer/id autopay) connect-id (customer/id customer) (account/email (customer/account customer)) (:id source))
        #_(create-connect-source! (customer/id customer)
                                  (customer/id autopay)
                                  connect-id
                                  (:id source))))))


(defn- customer-sources-txdata
  [db customer scustomer]
  (let [sources (get-in scustomer [:sources :data])
        txdata (map
                (fn [{:keys [id fingerprint object]}]
                  (tb/assoc-when
                   {:payment-source/id          id
                    :payment-source/fingerprint fingerprint
                    :payment-source/type        (->source-type object)
                    :payment-source/active      true}
                   :payment-source/payment-types
                   (cond
                     (= object "bank_account")
                     [:payment.type/rent
                      :payment.type/deposit]
                     (and (= object "card")
                          (= id (:default_source scustomer)))
                     :payment.type/order)))
                sources)]
    (propagate-ops-customer! db customer sources)
    txdata))


(defn ^{:added "1.10.0"} enrich-existing-customers-with-teller-stuff
  [teller conn]
  (h/with-token (config/stripe-secret-key config)
    (let [db        (d/db conn)
          customers (non-autopay-customers db)]
      (->> (mapv
            (fn [customer]
              (let [account   (customer/account customer)
                    autopay   (customer/autopay db account)
                    community (community-for-account db account)
                    scustomer (scustomer/fetch (customer/id customer))]
                (cond-> {:db/id (td/id customer)
                         :customer/id (d/squuid)
                         :customer/email (account/email account)}

                  (some? community)
                  (assoc :customer/default-property
                         (td/id (tproperty/by-community teller community)))

                  (some? autopay)
                  (assoc :customer/connected
                         (connected-customer-txdata autopay customer (get-in scustomer [:sources :data])))

                  true
                  (assoc :customer/payment-sources
                         (customer-sources-txdata db customer scustomer)))))
            customers)
           (d/transact conn)
           (deref)))))


(comment

  (do
    (def conn odin.datomic/conn)
    (def teller odin.teller/teller))


  (enrich-existing-customers-with-teller-stuff teller conn)

  )
