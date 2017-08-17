(ns odin.graphql.resolvers
  (:require [odin.graphql.resolvers.account :as account]
            [odin.graphql.resolvers.payment :as payment]
            [odin.graphql.resolvers.payment-source :as source]))


;; TODO: Authorization middleware


(def ^:private account-resolvers
  {;; fields
   :account/property   account/property
   :person/full-name   account/full-name
   ;; mutations
   :account/set-phone! account/set-phone!
   ;; queries
   :account/list       account/accounts
   :account/entry      account/entry})


(def ^:private payment-resolvers
  {;; fields
   :payment/external-id payment/external-id
   :payment/method      payment/method
   :payment/status      payment/status
   :payment/source      payment/source
   :payment/autopay?    payment/autopay?
   :payment/for         payment/payment-for
   ;; queries
   :payment/list        payment/payments
   })


(def ^:private payment-source-resolvers
  {;; fields
   :payment.source/type     source/type
   :payment.source/name     source/name
   :payment.source/payments source/payments
   ;; queries
   :payment.sources/list    source/sources
   })


(def resolvers
  (merge
   account-resolvers
   payment-resolvers
   payment-source-resolvers
   {:get (fn [k] (fn [_ _ v] (get v k)))}))
