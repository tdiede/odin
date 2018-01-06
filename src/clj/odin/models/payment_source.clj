(ns odin.models.payment-source
  (:require [blueprints.models.customer :as customer]
            [datomic.api :as d]
            [ribbon.customer :as rcu]
            [toolbelt.async :refer [<!? go-try]]
            [toolbelt.core :as tb]
            [toolbelt.predicates :as p]
            [ribbon.core :as ribbon]
            [clojure.spec.alpha :as s]))



(defn- source-customer [db source]
  (let [customer-id (d/q '[:find ?e .
                           :in $ ?customer-id
                           :where
                           [?e :stripe-customer/customer-id ?customer-id]]
                         db (:customer source))]
    (d/entity db [:stripe-customer/customer-id (:customer source)])))


(defn source-account [db source]
  (customer/account (source-customer db source)))


(defn sources-by-account
  "Produce all payment sources for a given `account`."
  [stripe customer]
  (go-try
   (let [customer' (<!? (rcu/fetch stripe (customer/id customer)))]
     (->> (rcu/sources customer')
          ;; inject the customer for field resolvers
          (map #(assoc % ::customer customer'))))))


(defn service-source
  "Produce the service source for `account`."
  [db stripe account]
  (go-try
   (when-let [customer (customer/by-account db account)]
     (let [sources   (<!? (sources-by-account stripe customer))
           customer' (<!? (rcu/fetch stripe (customer/id customer)))]
       (when (= "card" (rcu/default-source-type customer'))
         (tb/find-by #(= (:default_source customer') (:id %)) sources))))))


(defn fetch-source
  "Fetch the source by fetching the `requester`'s customer entity and attempting
  to fetch the source present on it. This mandates that the `source-id` actually
  belong to the requesting account."
  [db stripe account source-id]
  (let [customer (customer/by-account db account)]
    (rcu/fetch-source stripe (customer/id customer) source-id)))

(s/fdef fetch-source
        :args (s/cat :db p/db?
                     :stripe ribbon/conn?
                     :account p/entity?
                     :source-id string?)
        :ret p/chan?)
