(ns odin.models.metrics.orders
  (:require [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [datomic.api :as d]))


(defn query
  [db & {:keys [statuses from to]
         :or   {statuses #{:order.status/charged}}}]
  (->> (d/q '[:find [?o ...]
              :in $ [?status ...]
              :where
              [?o :order/status ?status]]
            db statuses)
       (map (partial d/entity db))))


(defn- relevant-license
  "Produces either the /active/ license for `account`, or the first one present
  in the absence of a license."
  [db account]
  (or (member-license/active db account) (first (:account/licenses account))))


(defn- order-property
  "Produce the property that a given `order` is associated with."
  [db order]
  (let [license (relevant-license db (order/account order))]
    (member-license/property license)))


(defn by-property
  [db orders]
  (group-by (partial order-property db) orders))




(comment
  (require '[odin.datomic :refer [conn]])


  (do
    (require '[datomic.api :as d])
    (require '[starcity.datomic :refer [conn]])
    (require '[blueprints.models.order :as order])
    (require '[blueprints.models.member-license :as member-license]))

  (->> (query (d/db conn) :statuses #{:order.status/charged
                                      :order.status/pending})
       (by-property (d/db conn))
       )

  )
