(ns admin.services.orders.events
  (:require [admin.services.orders.db :as db]
            [admin.routes :as routes]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; generic ======================================================================
;; ==============================================================================


(defn- orders-query-params
  [{:keys [statuses from to datekey accounts]}]
  (tb/assoc-when
   {}
   :accounts (when (some? accounts) (vec accounts))
   :to      (when (some? to) (.toISOString to))
   :from    (when (some? from) (.toISOString from))
   :datekey (when (or (some? to) (some? from)) datekey)
   :statuses (when-not (contains? statuses :all)
               (vec statuses))))


(reg-event-fx
 :services.orders/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:orders {:params (orders-query-params params)}
                 [:id :price :created :quantity :name :desc :status :billed_on
                  [:account [:id :name :email [:property [:id :name]]]]
                  [:service [:id :name :code :billed :price]]
                  [:property [:id :name]]
                  [:payments [:id :amount]]]]]
               :on-success [::orders-query k params]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::orders-query
 [(path db/path)]
 (fn [{db :db} [_ k params response]]
   {:db       (->> (get-in response [:data :orders])
                   (norms/normalize db :orders/norms))
    :dispatch [:ui/loading k false]}))


(reg-event-fx
 :services.order/fetch
 [(path db/path)]
 (fn [{db :db} [k order-id]]
   {:dispatch-n [[:ui/loading k true]
                 [:history/fetch order-id]]
    :graphql    {:query
                 [[:order {:id order-id}
                   [:id :price :created :quantity :name :request :summary :status
                    :billed_on :fulfilled_on :projected_fulfillment :cost
                    [:line_items [:id :desc :cost :price]]
                    [:fields [:id :label :index :value :type]]
                    [:variant [:id :name :price]]
                    [:account [:id :name [:service_source [:id]]]]
                    [:service [:id :name :description :code :cost :billed :price]]
                    [:property [:id :name]]
                    [:payments [:id :amount :description :paid_on]]]]]
                 :on-success [::order-fetch k]
                 :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::order-fetch
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [order (-> (get-in response [:data :order])
                   (update :fields #(sort-by :index %)))]
     {:db       (norms/assoc-norm db :orders/norms (:id order) order)
      :dispatch [:ui/loading k false]})))


;; ==============================================================================
;; entry ========================================================================
;; ==============================================================================


(defmethod routes/dispatches :services.orders/entry [route]
  (let [order-id (get-in route [:params :order-id])]
    [[:services.order/fetch (tb/str->int order-id)]]))


(reg-event-fx
 :services.order/refresh
 (fn [_ [_ order-id]]
   {:dispatch [:services.order/fetch order-id]}))


(reg-event-fx
 :services.order/place!
 (fn [_ [k {id :id} {:keys [send-notification projected-fulfillment]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:place_order {:id                    id
                               :notify                (boolean send-notification)
                               :projected_fulfillment (when-let [d projected-fulfillment]
                                                        (.toISOString d))}
                 [:id]]]
               :on-success [::place! k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::place!
 (fn [_ [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:services.order/fetch (get-in response [:data :place_order :id])]
                 [:modal/hide :services.order/place]]}))


(reg-event-fx
 :services.order/cancel!
 (fn [_ [k {id :id} {:keys [send-notification]}]]
   {:dispatch [:ui/loading k true]
    :graphql {:mutation
              [[:cancel_order {:id id :notify (boolean send-notification)} [:id]]]
              :on-success [::cancel! k]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::cancel!
 (fn [_ [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:services.order/fetch (get-in response [:data :cancel_order :id])]
                 [:modal/hide :services.order/cancel]]}))


(reg-event-fx
 :services.order/fulfill!
 (fn [_ [k {id :id} {:keys [send-notification actual-fulfillment process-charge]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:fulfill_order {:id           id
                                 :fulfilled_on (.toISOString actual-fulfillment)
                                 :charge       (boolean process-charge)
                                 :notify       (boolean send-notification)}
                 [:id]]]
               :on-success [::fulfill! k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fulfill!
 (fn [_ [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:services.order/fetch (get-in response [:data :fulfill_order :id])]
                 [:modal/hide :services.order/fulfill]]}))


(reg-event-fx
 :services.order/charge!
 (fn [_ [k {id :id}]]
   {:dispatch-n [[:ui/loading k true]
                 [:services.order/fetch id]]
    :graphql    {:mutation
                 [[:charge_order {:id id} [:id]]]
                 :on-success [::charge! k]
                 :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::charge!
 (fn [_ [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:services.order/fetch (get-in response [:data :charge_order :id])]
                 [:modal/hide :services.order/charge]]}))


(reg-event-db
 :services.order/editing
 [(path db/path)]
 (fn [db [_ order-id is-editing]]
   (assoc-in db [:services.order/editing order-id] is-editing)))


(reg-event-fx
 :services.order/update!
 (fn [_ [k order params]]
   (let [uparams (reduce
                  (fn [acc [k v]]
                    (if (not= v (get order k))
                      (assoc acc k v)
                      acc))
                  {}
                  params)]
     {:dispatch [:ui/loading k true]
      :graphql  {:mutation
                 [[:update_order {:id     (:id order)
                                  :params (dissoc uparams :id)}
                   [:id]]]
                 :on-success [::update! k]
                 :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::update!
 (fn [_ [_ k response]]
   (let [order-id (get-in response [:data :update_order :id])]
     {:dispatch-n [[:ui/loading k false]
                   [:services.order/fetch order-id]
                   [:services.order/editing order-id false]]})))


;; ==============================================================================
;; list =========================================================================
;; ==============================================================================


(defmethod routes/dispatches :services.orders/list [{params :params}]
  (if (empty? params)
    [[:services.orders/set-default-route]]
    [[:services.orders/fetch (db/parse-query-params params)]]))


(reg-event-fx
 :services.orders/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   {:route (db/params->route (:params db))}))


(reg-event-fx
 :services.orders/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:services.orders/query query-params]
    :db       (assoc db :params query-params)}))


(reg-event-fx
 :orders.status/select
 [(path db/path)]
 (fn [{db :db} [_ status]]
   (let [statuses  (get-in db [:params :statuses])
         statuses' (cond
                     (= status :all)             #{:all}
                     (contains? statuses :all)   (conj (disj statuses :all) status)
                     (contains? statuses status) (disj statuses status)
                     :otherwise                  (conj statuses status))]
     {:route (db/params->route (assoc (:params db) :statuses (if (empty? statuses') #{:all} statuses')))})))


(reg-event-fx
 :orders.range/change
 [(path db/path)]
 (fn [{db :db} [_ from to]]
   {:route (db/params->route (assoc (:params db) :from from :to to))}))


(reg-event-fx
 :services.orders/datekey
 [(path db/path)]
 (fn [{db :db} [_ datekey]]
   {:route (db/params->route (assoc (:params db) :datekey datekey))}))


(reg-event-fx
 :services.orders/search-members
 [(path db/path)]
 (fn [{db :db} [k query]]
   {:dispatch-throttle {:id              k
                        :window-duration 500
                        :leading?        false
                        :trailing?       true
                        :dispatch        [::search-members k query]}}))


(reg-event-fx
 ::search-members
 [(path db/path)]
 (fn [{db :db} [_ k query]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query      [[:accounts {:params {:roles [:member]
                                                 :q     query}}
                             [:id :name :email]]]
               :on-success [::search-members-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::search-members-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db      (assoc db :accounts (get-in response [:data :accounts]))
    :dispatch [:ui/loading k false]}))


(reg-event-fx
 :services.orders/select-members
 [(path db/path)]
 (fn [{db :db} [_ selected]]
   {:db    (assoc db :selected-accounts selected)
    :route (db/params->route (assoc (:params db) :accounts (map (comp tb/str->int :key) selected)))}))



(reg-event-fx
 :orders.filters/reset
 [(path db/path)]
 (fn [{db :db} _]
   {:db    (dissoc db :selected-accounts)
    :route (db/params->route db/default-params)}))
