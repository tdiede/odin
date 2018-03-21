(ns member.services.events
  (:require [akiroz.re-frame.storage :refer [reg-co-fx!]]
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
 (fn [_ [k]]
   {:dispatch-n [[::save-cart []]
                 [:ui/loading k false]]}))


(defmethod routes/dispatches :services/book
  [{:keys [params page requester] :as route}]
  (if (empty? params)
    [[:services/set-default-route route]]
    [[:services/fetch (db/parse-query-params page params)]
     [:services/fetch-catalogs]
     [::load-cart]]))


(defmethod routes/dispatches :services/manage
  [_]
  [[::load-cart]])


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
     {:graphql {:query [[:services {:params {:properties [property-id]}}
                         [:id :name :description :price :catalogs
                          [:fields [:id :index :label :type :required
                                    [:options [:index :label :value]]]]]]]
                :on-success [:services/catalogs k]
                :on-failure [:graphql/failure k]}})))


(reg-event-fx
 :services/catalogs
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [services (sort-by #(clojure.string/lower-case (:name %)) (get-in response [:data :services]))
         clist (sort (distinct (reduce #(concat %1 (:catalogs %2)) [] services)))]
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
 (fn [{db :db} [_ {:keys [id name description fields]}]]
   (let [service {:id          id
                  :name        name
                  :description description}]
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
     {:dispatch-n [[:services.add-service/close]
                   [::save-cart new-cart]]})))


(defn construct-order-fields [fields]
  (map
   (fn [field]
     (let [order-field (tb/assoc-when
                        {:service_field (:id field)}
                        :value (:value field))]
       order-field))
   fields))

(defn create-order-params
  "Constructs `mutate_order_params` from app db"
  [cart account]
  (map
   (fn [item]
     (let [fields (construct-order-fields (:fields item))
           order  (tb/assoc-when
                   {:account (:id account)
                    :service (:service item)}
                   :fields  fields)]
       order))
   cart))


(reg-event-fx
 :services.cart/submit
 [(path db/path)]
 (fn [{db :db} [k account]]
   (let [order-params (create-order-params (:cart db) account)
         ]
     (.log js/console "order params" order-params)
     {:dispatch  [:ui/loading k true]
      :graphql {:mutation   [[:order_create_many {:params order-params}
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
 (fn [{db :db} [_ index]]
   (let [new-cart (reindex-cart (remove #(= index (:index %)) (:cart db)))]
     {:dispatch [::save-cart new-cart]})))


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
                       (if (= (:id item) (:id (:adding db)))
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
   {:dispatch-n [[:ui/loading k false]
                 [:modal/hide :payment.source/add]]
    :route      (routes/path-for :services/cart)}))
