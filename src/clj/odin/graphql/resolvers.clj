(ns odin.graphql.resolvers
  (:require [odin.graphql.resolvers.account :as account]
            [odin.graphql.resolvers.deposit :as deposit]
            [odin.graphql.resolvers.payment :as payment]
            [odin.graphql.resolvers.payment-source :as source]
            [odin.graphql.resolvers.member-license :as member-license]
            [odin.graphql.resolvers.unit :as unit]))


;; TODO: Authorization middleware


(def ^:private account-resolvers
  {;; fields
   :account/active-license account/active-license
   :account/deposit        account/deposit
   :account/property       account/property
   :account/role           account/role
   :person/full-name       account/full-name
   ;; mutations
   :account/set-phone!     account/set-phone!
   ;; queries
   :account/list           account/accounts
   :account/entry          account/entry})


(def ^:private deposit-resolvers
  {;;fields
   :deposit/amount-remaining deposit/amount-remaining
   :deposit/amount-paid      deposit/amount-paid
   :deposit/amount-pending   deposit/amount-pending
   :deposit/refund-status    deposit/refund-status
   })


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
   :payment.source/autopay?        source/autopay?
   :payment.source/type            source/type
   :payment.source/name            source/name
   :payment.source/payments        source/payments
   :payment.source/default?        source/default?
   ;; queries
   :payment.sources/list           source/sources
   ;; mutations
   :payment.sources/delete!        source/delete!
   :payment.sources/add-source!    source/add-source!
   :payment.sources/verify-bank!   source/verify-bank!
   :payment.sources/set-autopay!   source/set-autopay!
   :payment.sources/unset-autopay! source/unset-autopay!
   :payment.sources/set-default!   source/set-default!
   })


(def ^:private member-license-resolvers
  {;;fields
   :member-license/status member-license/status})


(def ^:private unit-resolvers
  {;;fields
   :unit/number unit/number})


(def resolvers
  (merge
   account-resolvers
   deposit-resolvers
   payment-resolvers
   payment-source-resolvers
   member-license-resolvers
   unit-resolvers
   {:get (fn [& ks] (fn [_ _ v] (get-in v ks)))}))
