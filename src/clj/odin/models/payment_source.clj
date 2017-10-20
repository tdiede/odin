(ns odin.models.payment-source
  (:require [blueprints.models.customer :as customer]
            [ribbon.customer :as rcu]
            [toolbelt.async :refer [<!? go-try]]
            [toolbelt.core :as tb]))

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
