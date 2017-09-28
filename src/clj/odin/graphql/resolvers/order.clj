(ns odin.graphql.resolvers.order
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [blueprints.models.payment :as payment]))

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
  (if-let [s (order/status order)]
    (-> s name keyword)))


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
        license (-> order order/account :account/licenses first)]
    (if (some? license)
      (member-license/property license)
      (resolve/resolve-as nil {:message  "Cannot locate property for this order!"
                               :order-id (:db/id order)}))))


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
      (resolve/resolve-as nil {:message  (.getMessage t)
                               :err-data (ex-data t)}))))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn create!
  "Create a new order."
  [{:keys [conn]} {{:keys [account service variant desc quantity price]} :params} _]
  (let [[account service] (td/entities (d/db conn) account service)
        order             (order/create account service
                                        (tb/assoc-when
                                         {}
                                         :desc desc
                                         :price price
                                         :variant variant
                                         :quantity quantity))]
    @(d/transact conn [order])
    (d/entity (d/db conn) [:order/uuid (:order/uuid order)])))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :order/list [_ account _]
  (account/admin? account))


(defmethod authorization/authorized? :order/create!
  [{conn :conn} account {account-id :account}]
  (or (account/admin? account) (= account-id (:db/id account))))


(def resolvers
  {;; fields
   :order/price     price
   :order/name      order-name
   :order/status    status
   :order/billed-on billed-on
   :order/property property
   ;; queries
   :order/list      orders
   ;; mutations
   :order/create! create!
   })


;;; Set proper dates on order statuses

(comment


  (defn- all-orders [db]
    (->> (d/q '[:find [?o ...]
                :where
                [?o :order/account _]]
              db)
         (map (partial d/entity db))))


  (defn- charge-status->payment-status [status]
    (case status
      :charge.status/succeeded :payment.status/paid
      :charge.status/failed    :payment.status/failed
      :payment.status/pending))


  (defn add-order-status [db order]
    (when (nil? (order/status order))
      (let [[status entity] (if-let [charge (:stripe/charge order)]
                              [:order.status/charged charge]
                              [:order.status/pending order])]
        [[:db/add (:db/id order) :order/status status]
         {:db/id "datomic.tx" :db/txInstant (td/updated-at db entity)}])))


  (defn add-order-payment [db order]
    (when-some [charge (:stripe/charge order)]
      {:db/id          (:db/id order)
       :order/payments (payment/create (order/computed-price order) (order/account order)
                                       :for :payment.for/order
                                       :status (charge-status->payment-status (:charge/status charge))
                                       :charge-id (:charge/stripe-id charge))}))


  (let [conn odin.datomic/conn]
    (doseq [order (all-orders (d/db conn))]
      (when-let [tx (add-order-status (d/db conn) order)]
        (try
          @(d/transact-async conn (tb/conj-when tx (add-order-payment (d/db conn) order)))
          (catch Throwable t
            (timbre/errorf t "failed to add status & payment: %s" (:db/id order)))))))

  )
