(ns member.services.events
  (:require [akiroz.re-frame.storage :refer [reg-co-fx!]]
            [antizer.reagent :as ant]
            [clojure.string :as string]
            [member.routes :as routes]
            [member.services.db :as db]
            [re-frame.core :refer [reg-event-fx reg-event-db path inject-cofx]]
            [toolbelt.core :as tb]))


(reg-co-fx! db/path
            {:fx   :cart
             :cofx :cart})


(reg-event-fx
 ::load-cart
 [(inject-cofx :cart) (path db/path)]
 (fn [{:keys [cart db]} _]
   {:db (assoc db :cart (or cart []))}))


(reg-event-fx
 ::save-cart
 [(path db/path)]
 (fn [{:keys [db]} [_ new-cart]]
   {:db   (assoc db :cart new-cart)
    :cart new-cart}))


(reg-event-fx
 ::clear-cart
 (fn [_ [_ k]]
   {:dispatch-n   [[::save-cart []]
                   [:ui/loading k false]]
    :notification [:success "Your premium service orders have been placed!"]
    :route        (routes/path-for :services/active-orders)}))


(defmethod routes/dispatches :services/book
  [{:keys [params page requester] :as route}]
  (if (empty? params)
    [[:services/set-default-route route]]
    [[:services/fetch (db/parse-query-params page params)]
     [:services/fetch-catalogs]
     [:services/fetch-orders (:id requester)]
     [::load-cart]]))


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


(defn parse-special-chars [str]
  (string/replace str "&amp;" "&"))


(reg-event-fx
 :services/fetch-orders
 (fn [{db :db} [k account]]
   {:graphql {:query [[:orders {:params {:accounts [account]}}
                       [:id :name :price :status :created :billed :billed_on :fulfilled_on :updated
                        [:payments [:id :amount :status :paid_on]]
                        [:fields [:id :label :value :type :index]]]]]
              :on-success [::fetch-orders k]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-orders
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [orders (->> (get-in response [:data :orders])
                     (map #(assoc % :name (parse-special-chars (:name %)))))]
     {:db (assoc db :orders orders)})))


(reg-event-fx
 :services/fetch-catalogs
 (fn [{db :db} [k]]
   {:dispatch-n [[:ui/loading k true]
                 [::fetch-property k (get-in db [:account :id])]]}))


(reg-event-fx
 ::fetch-property
 (fn [{db :db} [_ k account-id]]
   {:graphql {:query      [[:account {:id account-id}
                            [[:property [:id]]]]]
              :on-success [::fetch-catalogs k]
              :on-failure [:graphql/failure k]}}))


;; when we implement the `active` attribute for services
;; will need to add `:active true` to `query params`
;; we dont want to show members inactive services
(reg-event-fx
 ::fetch-catalogs
 (fn [{db :db} [_ k response]]
   (let [property-id (get-in response [:data :account :property :id])]
     {:graphql {:query      [[:services {:params {:properties [property-id]
                                                  :active     true}}
                              [:id :name :description :price :catalogs :active :billed
                               [:fields [:id :index :label :type :required
                                         [:options [:index :label :value]]]]]]]
                :on-success [:services/catalogs k]
                :on-failure [:graphql/failure k]}})))


(defn onboarding? [service]
  (let [svc-cat (set (:catalogs service))]
    (if (= #{:onboarding} svc-cat)
      true
      false)))


(reg-event-fx
 :services/catalogs
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [services (->> (get-in response [:data :services])
                       (remove onboarding?)
                       (map #(assoc % :name (parse-special-chars (:name %)) :description (parse-special-chars (:description %))))
                       (sort-by #(string/lower-case (:name %))))
         clist (->> (reduce #(concat %1 (:catalogs %2)) [] services)
                    (distinct)
                    (remove #(= :onboarding %))
                    (sort))]
     {:db (assoc db :catalogs clist :services services)})))


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
 (fn [{db :db} [_ {:keys [id name description price fields]}]]
   (let [service {:id          id
                  :name        name
                  :description description
                  :price       price}]
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
   (let [{:keys [id name description price]} (:adding db)
         adding                              {:index       (count (:cart db))
                                              :service     id
                                              :name        name
                                              :description description
                                              :price       price
                                              :fields      (:form-data db)}
         new-cart                            (conj (:cart db) adding)]
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
      :value (if (= type :text)
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
    :notification [:success "Payment method added!" {:description "You van now pay for premium services with your credit card on file"}]
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
   (let [order-name (->> (get-in response [:data :cancel_order :name])
                         (parse-special-chars))]
     {:dispatch-n   [[:ui/loading k false]
                     [:services/fetch-orders account-id]]
      :db           (dissoc db :canceling)
      :notification [:success (str order-name " has been canceled")]})))
