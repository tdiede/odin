(ns member.services.events
  (:require [akiroz.re-frame.storage :refer [reg-co-fx!]]
            [antizer.reagent :as ant]
            [clojure.string :as string]
            [member.routes :as routes]
            [member.services.db :as db]
            [re-frame.core :refer [reg-event-fx reg-event-db path inject-cofx]]
            [iface.utils.time :as time]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]
            [clojure.set :as set]))


(reg-co-fx! db/path
            {:fx   :store
             :cofx :store})


(reg-event-fx
 ::load-cart
 [(inject-cofx :store) (path db/path)]
 (fn [{:keys [store db]} _]
   {:dispatch [::check-cart-last-modified]}))


(reg-event-fx
 ::check-cart-last-modified
 [(inject-cofx :store) (path db/path)]
 (fn [{:keys [store db]} _]
   (let [last-modified (or (:last-modified store) (time/now))]
     (if (>= (* -1 (time/days-between last-modified)) 2)
       {:dispatch [::load-cart-empty]}
       {:dispatch [::check-service-modified]}))))


(reg-event-fx
 ::check-service-modified
 [(inject-cofx :store) (path db/path)]
 (fn [{:keys [store db]} _]
   (let [svc<->updated  (map #(assoc {} :id (:id %) :updated (:updated_at %)) (:services db))
         cart<->updated (map #(assoc {} :id (:service %) :updated (:updated_at %)) (:cart db))
         all-matches?   (reduce
                         (fn [matched {:keys [id updated]}]
                           (and matched
                                (= updated
                                   (:updated
                                    (tb/find-by #(= (:id %) id) svc<->updated)))))
                         true
                         cart<->updated)]
     (if all-matches?
       {:dispatch [::load-cart-from-store]}
       {:dispatch [::load-cart-empty]}))))


(reg-event-fx
 ::load-cart-from-store
 [(inject-cofx :store) (path db/path)]
 (fn [{:keys [store db]} _]
   {:db (assoc db :cart (or (:cart store) []))}))


(reg-event-fx
 ::load-cart-empty
 [(path db/path)]
 (fn [db _]
   {:dispatch [::save-cart []]}))


(reg-event-fx
 ::save-cart
 [(inject-cofx :store) (path db/path)]
 (fn [{:keys [store db]} [_ new-cart]]
   {:db    (assoc db :cart new-cart)
    :store (assoc store
                  :cart new-cart
                  :last-modified (time/moment->iso (time/now)))}))


(reg-event-fx
 ::clear-cart
 (fn [_ [_ k]]
   {:dispatch-n   [[::save-cart []]
                   [:ui/loading k false]]
    :notification [:success "Your orders have been placed!"]
    :route        (routes/path-for :services/active-orders)}))


(defmethod routes/dispatches :services/book
  [{:keys [params page requester] :as route}]
  [[:services/fetch (db/parse-query-params page params)]
   [:services/fetch-catalogs]
   [:services/fetch-orders (:id requester)]
   [::load-cart]])


(defmethod routes/dispatches :services/active-orders
  [{:keys [requester] :as route}]
  [[:services/fetch-orders (:id requester)]
   [::load-cart]])


(defmethod routes/dispatches :services/subscriptions
  [{:keys [requester] :as route}]
  [[:services/fetch-orders (:id requester)]
   [::load-cart]])


(defmethod routes/dispatches :services/order-history
  [{:keys [requester] :as route}]
  [[:services/fetch-orders (:id requester)]
   [::load-cart]])


(defmethod routes/dispatches :services/cart
  [{:keys [requester] :as route}]
  [[:payment-sources/fetch (:id requester)]
   [::load-cart]])


(reg-event-fx
 :services/set-default-route
 [(path db/path)]
 (fn [{db :db} [_ {page :page}]]
   {:route (db/params->route page (:params db))}))


(reg-event-fx
 :services/fetch
 [(path db/path)]
 (fn [{db :db} [_ query-params]]
   {:db (assoc db :params query-params)}))


(reg-event-fx
 :services/fetch-orders
 (fn [{db :db} [k account]]
   {:graphql {:query      [[:orders {:params {:accounts [account]}}
                            [:id :name :price :status :created :billed :billed_on :fulfilled_on :updated
                             [:payments [:id :amount :status :paid_on]]
                             [:fields [:id :label :value :type :index]]
                             [:service [:type]]]]]
              :on-success [::fetch-orders k]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-orders
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [orders (->> (get-in response [:data :orders])
                     (map #(-> (assoc % :name (:name %) :type (:type (:service %)))
                               (dissoc :service))))]
     {:db (assoc db :orders orders)})))


(reg-event-fx
 :services/fetch-catalogs
 (fn [{db :db} [k]]
   {:dispatch-n [[:ui/loading k true]
                 [::fetch-property k (get-in db [:account :id]) [:services/catalogs]]]}))


(reg-event-fx
 ::fetch-property
 (fn [{db :db} [_ k account-id on-success]]
   (when (nil? on-success)
     (throw (ex-info "Please provide an `on-success` event." {})))
   {:graphql {:query      [[:account {:id account-id}
                            [[:property [:id]]]]]
              :on-success [::fetch-catalogs k on-success]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-catalogs
 (fn [_ [_ k on-success response]]
   (when (nil? on-success)
     (throw (ex-info "Please provide an `on-success` event." {})))
   (let [property-id (get-in response [:data :account :property :id])]
     {:graphql {:query      [[:services {:params {:properties [property-id]
                                                  :active     true}}
                              [:id :name :description :price :catalogs :active :billed :updated_at
                               [:fees [:id :name :description :price]]
                               [:fields [:id :index :label :type :required
                                         [:options [:index :label :value]]]]]]]
                :on-success [::extract-services k on-success]
                :on-failure [:graphql/failure k]}})))


(defn only-onboarding? [service]
  (let [svc-cat (set (:catalogs service))]
    (= #{:onboarding} svc-cat)))


(reg-event-fx
 ::extract-services
 (fn [_ [_ k on-success response]]
   {:dispatch (conj on-success k (get-in response [:data :services]))}))


(reg-event-fx
 :services/catalogs
 [(path db/path)]
 (fn [{db :db} [_ k services]]
   (let [services (->> (remove only-onboarding? services)
                       (map #(assoc % :name (:name %) :description (:description %)))
                       (sort-by #(string/lower-case (:name %))))
         clist    (->> (reduce #(concat %1 (:catalogs %2)) [] services)
                       (distinct)
                       (remove #(= :onboarding %))
                       (sort))]
     (tb/assoc-when
      {:dispatch [:ui/loading k false]
       :db       (-> (assoc db :catalogs clist :services services)
                     (norms/normalize :services/norms services))}
      :route (when (nil? (get-in db [:params :category]))
               (routes/path-for :services/book :query-params {:category (name (first clist))}))))))


(reg-event-fx
 :services.section/select
 [(path db/path)]
 (fn [_ [_ section]]
   (let [page (keyword (str "services/" section))]
     {:route (routes/path-for page)})))


(reg-event-db
 :services.add-service.form/update
 [(path db/path)]
 (fn [db [_ index value]]
   (update db :form-data
           (fn [fields]
             (map
              (fn [field]
                (if (= (:index field) index)
                  (assoc field :value value)
                  field))
              fields)))))


(reg-event-db
 :services.add-service.form/reset
 [(path db/path)]
 (fn [db _]
   (dissoc db :form-data)))


(reg-event-fx
 :services.add-service/show
 [(path db/path)]
 (fn [{db :db} [_ {:keys [id name description price fields billed updated_at fees]}]]
   (let [service {:id          id
                  :name        name
                  :description description
                  :price       price
                  :billed      billed
                  :updated_at  updated_at
                  :fees        fees}]
     {:dispatch [:modal/show db/modal]
      :db       (assoc db :adding service :form-data (sort-by :index fields))})))


(reg-event-fx
 :services.add-service/close
 [(path db/path)]
 (fn [{db :db} _]
   {:dispatch-n [[:services.add-service.form/reset]
                 [:modal/hide db/modal]]}))


(reg-event-fx
 :services.add-service/add
 [(path db/path) ]
 (fn [{db :db} _]
   (let [{:keys [id name description fees price billed updated_at] :as svc} (:adding db)
         adding                                   {:index       (count (:cart db))
                                                   :service     id
                                                   :updated_at  updated_at
                                                   :name        name
                                                   :description description
                                                   :price       price
                                                   :fees        fees
                                                   :billed      billed
                                                   :fields      (:form-data db)}
         new-cart                                 (conj (:cart db) adding)]
     {:dispatch-n   [[:services.add-service/close]
                     [::save-cart new-cart]]
      :notification [:success (str name " has been added to your cart")]})))


(defn construct-order-fields
  "When field type is of `text` we replace all instances of `\n` for a space"
  [fields]
  (map
   (fn [{:keys [id value type]}]
     (tb/assoc-when
      {:service_field id}
      :value (if (and (= type :text) (some? value))
               (string/replace value "\n" " ")
               value)))
   fields))


(defn create-order-params
  "Constructs `mutate_order_params` from app db"
  [cart account]
  (map
   (fn [item]
     (let [fields (construct-order-fields (:fields item))]
       (tb/assoc-when
        {:account (:id account)
         :service (:service item)}
        :fields  fields)))
   cart))


(reg-event-fx
 :services.cart/submit
 [(path db/path)]
 (fn [{db :db} [k account]]
   (let [order-params (create-order-params (:cart db) account)]
     {:dispatch [:ui/loading k true]
      :graphql  {:mutation   [[:order_create_many {:params order-params}
                               [:id]]]
                 :on-success [::clear-cart k]
                 :on-failure [:graphql/failure k]}})))


(defn reindex-cart [cart]
  (map-indexed
   (fn [i item]
     (assoc item :index i))
   (sort-by :index cart)))


(reg-event-fx
 :services.cart.item/remove
 [(path db/path)]
 (fn [{db :db} [_ index name]]
   (let [new-cart (reindex-cart (remove #(= index (:index %)) (:cart db)))]
     {:dispatch     [::save-cart new-cart]
      :notification [:success (str name " has been removed from your cart")]})))


;; this is the same as services.add-service/show, should probably rename that to make it generic
(reg-event-fx
 :services.cart.item/edit
 [(path db/path)]
 (fn [{db :db} [_ service fields]]
   {:dispatch [:modal/show db/modal]
    :db       (assoc db :adding service :form-data fields)}))


(reg-event-fx
 :services.cart.item/save-edit
 [(path db/path)]
 (fn [{db :db} _]
   (let [new-fields (:form-data db)
         new-cart   (map
                     (fn [item]
                       (if (= (:index item) (:index (:adding db)))
                         (assoc item :fields new-fields)
                         item))
                     (:cart db))]
     {:dispatch-n [[:services.add-service/close]
                   [::save-cart new-cart]]})))



;; =============================================================================
;; Add Card
;; =============================================================================

;; Cards have no `submit` event, as this is handled by the Stripe JS API.
;; We skip immediately to `success`, where we've
;; received a token for the new card from Stripe.

(reg-event-fx
 :services.cart.add.card/save-stripe-token!
 (fn [_ [k token]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation   [[:add_payment_source {:token token} [:id]]]
               :on-success [::services-create-card-source-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::services-create-card-source-success
 (fn [{:keys [db]} [_ k response]]
   {:dispatch-n   [[:ui/loading k false]
                   [:services.cart/submit (:account db)]
                   [:modal/hide :payment.source/add]]
    :notification [:success "Payment method added!" {:description "You can now pay for services with your credit card on file"}]
    :route        (routes/path-for :services/cart)}))


;; ==============================================================================
;; orders =======================================================================
;; ==============================================================================


(reg-event-fx
 :services.order/cancel-order
 [(path db/path)]
 (fn [{db :db} [k id account-id]]
   {:dispatch [:ui/loading k true]
    :db       (assoc db :canceling id)
    :graphql  {:mutation   [[:cancel_order {:id     id
                                            :notify true}
                             [:id :name]]]
               :on-success [::order-cancel-success k account-id]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::order-cancel-success
 [(path db/path)]
 (fn [{db :db} [_ k account-id response]]
   (let [order-name (get-in response [:data :cancel_order :name])]
     {:dispatch-n   [[:ui/loading k false]
                     [:services/fetch-orders account-id]]
      :db           (dissoc db :canceling)
      :notification [:success (str order-name " has been canceled")]})))
