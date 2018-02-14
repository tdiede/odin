(ns odin.graphql.resolvers
  (:require [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [odin.graphql.resolvers.account :as account]
            [odin.graphql.resolvers.application :as application]
            [odin.graphql.resolvers.check :as check]
            [odin.graphql.resolvers.deposit :as deposit]
            [odin.graphql.resolvers.member-license :as member-license]
            [odin.graphql.resolvers.note :as note]
            [odin.graphql.resolvers.order :as order]
            [odin.graphql.resolvers.payment :as payment]
            [odin.graphql.resolvers.payment-source :as source]
            [odin.graphql.resolvers.property :as property]
            [odin.graphql.resolvers.referral :as referral]
            [odin.graphql.resolvers.service :as service]
            [odin.graphql.resolvers.unit :as unit]
            [toolbelt.datomic :as td]))

(def ^:private util-resolvers
  {:get            (fn [& ks] (fn [_ _ v] (get-in v ks)))
   :keyword/name   (fn [& ks] (fn [_ _ v] (keyword (name (get-in v ks)))))
   :entity/created (fn [{conn :conn} _ entity] (td/created-at (d/db conn) entity))
   :entity/updated (fn [{conn :conn} _ entity] (td/updated-at (d/db conn) entity))})


(defn resolvers []
  (->> (merge
        account/resolvers
        application/resolvers
        check/resolvers
        deposit/resolvers
        source/resolvers
        member-license/resolvers
        note/resolvers
        order/resolvers
        payment/resolvers
        property/resolvers
        service/resolvers
        referral/resolvers
        unit/resolvers)
       (reduce
        (fn [acc [k v]]
          (if (contains? (methods authorization/authorized?) k)
            (assoc acc k (authorization/wrap-authorize k v))
            (assoc acc k v)))
        {})
       (merge util-resolvers)))
