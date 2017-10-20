(ns odin.graphql.resolvers.order
  (:require [blueprints.models.account :as account]
            [blueprints.models.events :as events]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [blueprints.models.source :as source]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [toolbelt.async :refer [<!!?]]
            [odin.models.payment-source :as payment-source]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn price
  [_ _ order]
  (order/computed-price order))


(defn cost
  [_ _ order]
  (order/computed-cost order))


(defn order-name
  [_ _ order]
  (order/computed-name order))


(defn status
  [_ _ order]
  (-> order order/status name keyword))


(defn billed-on
  "Date that `order` was billed on."
  [{conn :conn} _ order]
  (or (order/billed-on order)
      (td/last-modified-to (d/db conn) (td/id order) :order/status :order.status/charged)))


(defn fulfilled-on
  "Date that `order` was fulfilled."
  [{conn :conn} _ order]
  (order/fulfilled-on order))


(defn property
  "The property that the member that placed this order lives in."
  [{conn :conn} _ order]
  (let [license (-> order order/account :account/licenses first)]
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


(defn order
  [{conn :conn} {order-id :id} _]
  (d/entity (d/db conn) order-id))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn create!
  "Create a new order."
  [{:keys [requester conn]} {{:keys [account service variant desc quantity price]} :params} _]
  (let [[account service] (td/entities (d/db conn) account service)
        order             (order/create account service
                                        (tb/assoc-when
                                         {}
                                         :desc desc
                                         :price price
                                         :variant variant
                                         :quantity quantity))]
    @(d/transact conn [order (source/create requester)])
    (d/entity (d/db conn) [:order/uuid (:order/uuid order)])))


;; TODO: notify
(defn place!
  "Place an order."
  [{:keys [requester conn]} {:keys [id projected_fulfillment notify]} _]
  (let [order (d/entity (d/db conn) id)]
    (if-not (order/pending? order)
      (resolve/resolve-as nil {:message  "Order must be in pending status!"
                               :err-data {:current-status (order/status order)
                                          :order-id       id}})
      (do
        @(d/transact conn [(tb/assoc-when
                            (order/is-placed id)
                            :order/projected-fulfillment projected_fulfillment)
                           (source/create requester)])
        (d/entity (d/db conn) id)))))


;; TODO: notify
(defn fulfill!
  "Fulfill an order."
  [{:keys [conn requester stripe]} {:keys [id fulfilled_on charge notify]} _]
  (let [order  (d/entity (d/db conn) id)
        source (<!!? (payment-source/service-source (d/db conn) stripe (order/account order)))]
    (cond
      (not (or (order/pending? order) (order/placed? order)))
      (resolve/resolve-as nil {:message  "Order must be in pending or placed status!"
                               :err-data {:current-status (order/status order)
                                          :order-id       id}})

      (and charge (nil? source))
      (resolve/resolve-as nil {:message  "Cannot charge order; no service source linked!"
                               :err-data {:order-id id}})

      (and charge (nil? (order/computed-price order)))
      (resolve/resolve-as nil {:message  "Cannot charge order without a price!"
                               :err-data {:order-id id}})

      :otherwise
      (do
        @(d/transact conn (concat
                           [(source/create requester)]
                           (if charge
                             [(events/process-order requester order)
                              [:db/add id :order/fulfilled-on fulfilled_on]
                              [:db/add id :order/status :order.status/processing]]
                             [(order/is-fulfilled id fulfilled_on)])))
        (d/entity (d/db conn) id)))))


;; TODO: notify?
(defn charge!
  "Charge an order."
  [{:keys [conn requester stripe]} {:keys [id]} _]
  (let [order  (d/entity (d/db conn) id)
        source (<!!? (payment-source/service-source (d/db conn) stripe (order/account order)))]
    (cond
      (nil? source)
      (resolve/resolve-as nil {:message  "Cannot charge order; no service source linked!"
                               :err-data {:order-id id}})

      (not (order/fulfilled? order))
      (resolve/resolve-as nil {:message  "Order must be in fulfilled status!"
                               :err-data {:order-id       id
                                          :current-status (order/status order)}})

      (nil? (order/computed-price order))
      (resolve/resolve-as nil {:message  "Cannot charge order without a price!"
                               :err-data {:order-id id}})

      :otherwise
      (do
        @(d/transact conn [[:db/add id :order/status :order.status/processing]
                           (source/create requester)
                           (events/process-order requester order)])
        (d/entity (d/db conn) id)))))


;; TODO: notify
(defn cancel!
  "Cancel a premium service order."
  [{:keys [requester conn]} {:keys [id notify]} _]
  @(d/transact conn [(order/is-canceled id) (source/create requester)])
  (d/entity (d/db conn) id))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defn- admin-or-owner? [db order-id account]
  (let [order (d/entity db order-id)]
    (or (account/admin? account) (= (:db/id (order/account order)) (:db/id account)))))


(defmethod authorization/authorized? :order/list [_ account _]
  (account/admin? account))


(defmethod authorization/authorized? :order/entry [{conn :conn} account params]
  (admin-or-owner? (d/db conn) (:id params) account))


(defmethod authorization/authorized? :order/create!
  [{conn :conn} account {account-id :account}]
  (or (account/admin? account) (= account-id (:db/id account))))


(defmethod authorization/authorized? :order/cancel!
  [{conn :conn} account {order-id :id}]
  (admin-or-owner? (d/db conn) order-id account))


(defmethod authorization/authorized? :order/charge!
  [{conn :conn} account {order-id :id}]
  (admin-or-owner? (d/db conn) order-id account))


(def resolvers
  {;; fields
   :order/price        price
   :order/cost         cost
   :order/name         order-name
   :order/status       status
   :order/billed-on    billed-on
   :order/fulfilled-on fulfilled-on
   :order/property     property
   ;; queries
   :order/list         orders
   :order/entry        order
   ;; mutations
   :order/create!      create!
   :order/place!       place!
   :order/fulfill!     fulfill!
   :order/charge!      charge!
   :order/cancel!      cancel!})
