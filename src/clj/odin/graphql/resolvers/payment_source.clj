(ns odin.graphql.resolvers.payment-source
  (:refer-clojure :exclude [type name])
  (:require [blueprints.models.customer :as customer]
            [clojure.core.async :refer [go]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [ribbon.customer :as rcu]
            [toolbelt.async :refer [<!?]]
            [datomic.api :as d]
            [ribbon.charge :as rch]))


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


(defn payments
  "Payments associated with this `source`."
  [{:keys [stripe]} _ source]
  ;; (let [result (resolve/resolve-promise)]
  ;;   (go
  ;;     (try
  ;;       ;; This pulls a bunch of charges.
  ;;       (<!? (rch/many stripe :source (:id source)))
  ;;       (catch Throwable t
  ;;         (resolve/deliver! result nil {:message  (str "Exception:" (.getMessage t))
  ;;                                       :err-data (ex-data t)}))))
  ;;   result)
  ;; TODO:
  []
  )


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
