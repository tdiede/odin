(ns odin.graphql.resolvers.order
  (:require [blueprints.models.account :as account]
            [blueprints.models.approval :as approval]
            [blueprints.models.events :as events]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.order :as order]
            [blueprints.models.source :as source]
            [clj-time.coerce :as c]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.resolvers.utils :refer [error-message]]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [toolbelt.async :refer [<!!?]]
            [odin.models.payment-source :as payment-source]
            [clojure.set :as set]
            [teller.payment :as tpayment]
            [teller.source :as tsource]
            [teller.customer :as tcustomer]
            [teller.plan :as plan]
            [teller.subscription :as subscription]
            [blueprints.models.service :as service]))

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


(defn payments
  "The payments made towards this `order`."
  [{teller :teller} _ order]
  (map (partial tpayment/by-entity teller) (order/payments order)))


(defn- property-for-account [db account]
  (case (account/role account)
    :account.role/onboarding (-> account approval/by-account approval/property)
    :account.role/member     (-> account :account/licenses first member-license/property)
    nil))


(defn property
  "The property that the member that placed this order lives in."
  [{conn :conn} _ order]
  (if-let [p (property-for-account (d/db conn) (order/account order))]
    p
    (resolve/resolve-as nil {:message  "cannot locate property for this order!"
                             :order-id (:db/id order)})))


(defn field-value
  "The field value according to the service-field-type"
  [{conn :conn} _ order-field]
  (let [svc-field (:order-field/service-field order-field)
        value-key (order/order-field-key svc-field)]
    (when (some? value-key)
      (value-key order-field))))


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
      (resolve/resolve-as nil {:message  (error-message t)
                               :err-data (ex-data t)}))))


(defn order
  [{conn :conn} {order-id :id} _]
  (d/entity (d/db conn) order-id))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn- use-order-price? [service {:keys [line_items variant]}]
  (let [vprice (:price (tb/find-by (comp (partial = variant) :db/id) (:service/variants service)))]
    (and (empty? line_items) (nil? vprice))))


(defn- use-order-cost? [service {:keys [line_items variant]}]
  (let [vcost (:cost (tb/find-by (comp (partial = variant) :db/id) (:service/variants service)))
        lcost (->> line_items (map (fnil :cost 0)) (apply +))]
    (and (zero? lcost) (nil? vcost))))


(defn parse-params
  "Parse to params and converts them into the needed format"
  [{:keys [fields] :as params}]
  params)


(defn parse-fields
  "Parse and convert fields to the needed format"
  [db fields]
  (map
   (fn [field]
     (-> (order/order-field (d/entity db (:service_field field)) (:value field))
         (tb/transform-when-key-exists
             {:order-field.value/number #(float (tb/str->int %))
              :order-field.value/date   c/to-date})))
   fields))


(defn prepare-order
  [db {:keys [account service line_items fields] :as params}]
  (let [line-items (when-not (empty? line_items)
                     (map #(order/line-item (:desc %) (:price %) (:cost %)) line_items))
        fields     (when-not (empty? fields)
                     (parse-fields db fields))]
    (order/create account service
                  (tb/assoc-when
                   {}
                   :lines line-items
                   :summary (:summary params)
                   :request (:request params)
                   :price (when (use-order-price? service params)
                            (:price params))
                   :cost (when (use-order-cost? service params)
                           (:cost params))
                   :variant (:variant params)
                   :quantity (:quantity params)
                   :fields (when fields
                             fields)))))


(defn- tally-fees
  [fee occurences]
  (* (/ (:service/price fee) 2) (inc occurences)))


(defn- prepare-fees
  [db params]
  (let [services (map :service params)
        account  ((comp :account first) params)]
    (->> (mapcat (comp :service/fees (partial d/entity db)) services)
         (filter (comp not nil?))
         (group-by identity)
         (reduce-kv
          (fn [m k v]
            (assoc m k {:fee   (:db/id k)
                        :price (tally-fees (first v) (count v))})) {})
         vals
         (map (fn [f] (order/create account (:fee f) {:price (:price f)}))))))


(defn create!
  "Create a new order."
  [{:keys [requester conn]} {params :params} _]
  (let [order (prepare-order (d/db conn) (parse-params params))]
    @(d/transact conn [order
                       (events/order-created requester (order/uuid order) true)
                       (source/create requester)])
    (order/by-uuid (d/db conn) (order/uuid order))))


(defn create-many!
  "Create many new orders"
  [{:keys [requester conn]} {params :params} _]
  (let [orders          (map (comp (partial prepare-order (d/db conn)) parse-params) params)
        fees            (prepare-fees (d/db conn) params)
        orders-and-fees (concat orders fees)]

    @(d/transact conn (concat
                       (conj orders-and-fees  (source/create requester))
                       (map
                        #(events/order-created requester (order/uuid %) true)
                        orders-and-fees)))
    (map #(order/by-uuid (d/db conn) (order/uuid %)) orders-and-fees)))


;; Given set of existing ids, examine set of `old` ids
(defn- update-line-item-tx
  [db {:keys [id desc cost price] :as item}]
  (let [entity (d/entity db id)]
    (cond-> []
      ;; update desc
      (not= desc (:line-item/desc entity))
      (conj [:db/add id :line-item/desc desc])

      ;; remove cost
      (and (nil? cost) (some? (:line-item/cost entity)))
      (conj [:db/retract id :line-item/cost (:line-item/cost entity)])

      ;; update cost
      (and (some? cost) (not= (float cost) (:line-item/cost entity)))
      (conj [:db/add id :line-item/cost (float cost)])

      ;; update price
      (and (some? price) (not= (float price) (:line-item/price entity)))
      (conj [:db/add id :line-item/price (float price)]))))


(defn- update-line-items-tx [db order line-items-params]
  (let [line-items   (order/line-items order)
        [new old]    [(remove (comp some? :id) line-items-params)
                      (filter (comp some? :id) line-items-params)]
        existing-ids (set (map :db/id line-items))
        update-ids   (set (map :id old))
        to-retract   (set/difference existing-ids update-ids)]
    (cond-> []
      (not (empty? to-retract))
      (concat (map #(vector :db.fn/retractEntity %) to-retract))

      (not (empty? old))
      (concat (remove empty? (mapcat (partial update-line-item-tx db) old)))

      (not (empty? new))
      (concat (order/update order {:lines (map (fn [{:keys [desc price cost]}]
                                                 (order/line-item desc price cost))
                                               new)})))))


(defn update!
  "Update an existing order."
  [{:keys [requester conn]} {:keys [id params]} _]
  (let [order (d/entity (d/db conn) id)]
    (if (empty? params)
      (resolve/resolve-as nil {:message  "Please provide something to update."
                               :order-id (:db/id order)})
      (do
        @(d/transact conn (concat
                           (order/update order (dissoc params :line_items))
                           [(source/create requester)]
                           (when-let [line-items (:line_items params)]
                             (update-line-items-tx (d/db conn) order line-items))))
        (d/entity (d/db conn) [:order/uuid (:order/uuid order)])))))


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
                           (events/order-placed requester order notify)
                           (source/create requester)])
        (d/entity (d/db conn) id)))))


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
                           [(source/create requester)
                            (events/order-fulfilled requester order notify)]
                           (if charge
                             [(events/process-order requester order)
                              [:db/add id :order/fulfilled-on fulfilled_on]
                              [:db/add id :order/status :order.status/processing]]
                             [(order/is-fulfilled id fulfilled_on)])))
        (d/entity (d/db conn) id)))))


;; charge ===============================


(defn- ensure-can-charge
  [ order]
  )


(defmulti ^:private process-order!
  (fn [_ order]
    (service/billed (order/service order))))


(defmethod process-order! :service.billed/once
  [{:keys [teller conn]} order]
  (let [account  (order/account order)
        customer (tcustomer/by-account teller account)
        price    (order/computed-price order)
        source   (tcustomer/source customer :payment.type/order)
        payment  (tpayment/create! customer price :payment.type/order
                                   {:source source})
        tx-res   @(d/transact conn
                              [{:db/id          (td/id order)
                                :order/payments [:payment/id (tpayment/id payment)]}])]
    (d/entity (:db-after tx-res) (td/id order))))


;;; impasse!

;; `teller` plans neeed to be created for for each service. This should probably
;; be structured as a migration...?


#_(defn- fetch-or-create-plan!
  [teller service]
  (or (plan/by-id teller (order/uuid order))
      (let []
        (plan/create! teller ()))))


;; (defn- subscribe!
;;   [teller order]
;;   )


(defmethod process-order! :service.billed/monthly
  [{:keys [teller conn]} order]
  order)


(defn charge!
  "Charge an order."
  [{:keys [teller requester conn] :as ctx} {:keys [id]} _]
  (let [customer (tcustomer/by-account teller requester)
        order    (d/entity (d/db conn) id)
        price    (order/computed-price order)
        source   (tcustomer/source customer :payment.type/order)]
    (cond
      (nil? source)
      (resolve/resolve-as nil {:message  "Cannot charge order; no service source linked!"
                               :err-data {:order-id id}})

      (not (order/fulfilled? order))
      (resolve/resolve-as nil {:message  "Order must be in fulfilled status!"
                               :err-data {:order-id       id
                                          :current-status (order/status order)}})

      (nil? price)
      (resolve/resolve-as nil {:message  "Cannot charge order without a price!"
                               :err-data {:order-id id}})

      :otherwise
      (try
        (process-order! ctx order)
        (catch Throwable t
          (timbre/error t ::order {:order-id id :source source})
          (resolve/resolve-as nil {:message  (error-message t)
                                   :err-data {:order-id id}}))))))


(defn cancel!
  "Cancel a premium service order."
  [{:keys [requester conn]} {:keys [id notify]} _]
  @(d/transact conn [(order/is-canceled id)
                     (events/order-canceled requester id notify)
                     (source/create requester)])
  (d/entity (d/db conn) id))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defn- admin-or-owner? [db order-id account]
  (let [order (d/entity db order-id)]
    (or (account/admin? account) (= (:db/id (order/account order)) (:db/id account)))))


(defmethod authorization/authorized? :order/list [{conn :conn} account {params :params}]
  (or (account/admin? account)
      (= (:accounts params) [(td/id account)])))


(defmethod authorization/authorized? :order/entry [{conn :conn} account params]
  (admin-or-owner? (d/db conn) (:id params) account))


(defmethod authorization/authorized? :order/create!
  [{conn :conn} account {account-id :account}]
  (or (account/admin? account) (= account-id (:db/id account))))


(defmethod authorization/authorized? :order/update!
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
   :order/payments     payments
   :order/property     property
   :order-field/value  field-value
   ;; queries
   :order/list         orders
   :order/entry        order
   ;; mutations
   :order/create!      create!
   :order/create-many! create-many!
   :order/update!      update!
   :order/place!       place!
   :order/fulfill!     fulfill!
   :order/charge!      charge!
   :order/cancel!      cancel!})
