(ns odin.graphql.resolvers.referral
  (:require [blueprints.models.account :as account]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]))

(defn query
  [{conn :conn} _ _]
  (->> (d/q '[:find [?e ...]
              :where
              [?e :referral/source _]]
            (d/db conn))
       (map (partial d/entity (d/db conn)))))


(defmethod authorization/authorized? :referrals/query [_ account _]
  (account/admin? account))


(def resolvers
  {:referrals/query query})
