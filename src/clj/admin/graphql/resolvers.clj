(ns admin.graphql.resolvers
  (:require [admin.graphql.resolvers.account :as account]))


(def ^:private account-resolvers
  {:accounts/list      account/accounts
   :account/property   account/property
   :account/set-phone! account/set-phone!
   :person/full-name   account/full-name})


(def resolvers
  (merge
   account-resolvers
   {:get (fn [k] (fn [_ _ v] (get v k)))}))
