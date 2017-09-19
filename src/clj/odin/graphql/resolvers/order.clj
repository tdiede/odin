(ns odin.graphql.resolvers.order
  (:require [blueprints.models.order :as order]
            [toolbelt.datomic :as td]
            [datomic.api :as d]
            [blueprints.models.member-license :as member-license]
            [taoensso.timbre :as timbre]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn price
  [_ _ order]
  (order/computed-price order))


(defn order-name
  [_ _ order]
  (order/computed-name order))


(defn status
  [_ _ order]
  (-> order order/status name keyword))


(defn billed-on
  "Date that `order` was billed on."
  [{conn :conn} _ order]
  (d/q '[:find ?date .
         :in $ ?o
         :where
         [?o :order/status :order.status/charged ?tx]
         [?tx :db/txInstant ?date]]
       (d/db conn) (td/id order)))


(defn property
  "The property that the member that placed this order lives in."
  [{conn :conn} _ order]
  (let [created (td/created-at (d/db conn) order)
        license (member-license/active (d/as-of (d/db conn) created)
                                       (order/account order))]
    (member-license/property license)))


;; =============================================================================
;; Queries
;; =============================================================================


(defn- parse-gql-params
  [{:keys [billed statuses] :as params}]
  (tb/assoc-when
   params
   :statuses (when-some [xs statuses]
               (map #(keyword "order.status" (name %)) xs))
   :billed (when-some [xs billed]
            (map #(keyword "service.billed" (name %)) xs))))


(defn- query-orders
  [db params]
  (->> (parse-gql-params params)
       (apply concat)
       (apply order/query db)))


(defn orders
  [{conn :conn} {params :params} _]
  (try
    (query-orders (d/db conn) params)
    (catch Throwable t
      (timbre/error t "error querying orders")
      (resolve/resolve-as nil {:message  (str "Exception:" (.getMessage t))
                               :err-data (ex-data t)}))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;; fields
   :order/price     price
   :order/name      order-name
   :order/status    status
   :order/billed-on billed-on
   :order/property property
   ;; queries
   :order/list      orders
   })
