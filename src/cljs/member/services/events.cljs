(ns member.services.events
  (:require [member.routes :as routes]
            [member.services.db :as db]
            [re-frame.core :refer [reg-event-fx reg-event-db path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :services/book
  [{:keys [params page] :as route}]
  (if (empty? params)
    [[:services/set-default-route route]]
    [[:services/fetch (db/parse-query-params page params)]]))


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
 :services.section/select
 [(path db/path)]
 (fn [_ [_ section]]
   (let [page (keyword (str "services/" section))]
     {:route (routes/path-for page)})))


(reg-event-db
 :services.add-service.form/update
 [(path db/path)]
 (fn [db [_ key value]]
   (update db :form-data
           (fn [fields]
             (map
              (fn [field]
                (if (= (:key field) key)
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
 (fn [{db :db} [_ {:keys [service fields]}]]
   {:dispatch [:modal/show db/modal]
    :db       (assoc db :adding service :form-data fields)}))


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
   (let [{:keys [id title description price]} (:adding db)
         adding                               {:id          (db/rand-id) ;; gen unique id so we can remove items by item-id
                                               :service-id  id ;; not sure if needed, but its here
                                               :title       title
                                               :description description
                                               :price       price
                                               :fields      (:form-data db)}]
     {:db       (update db :cart conj adding)
      :dispatch [:services.add-service/close]})))


;; this removes all of the services with the same service id... we need to only remove the selected item
(reg-event-db
 :services.cart.item/remove
 [(path db/path)]
 (fn [db [_ id]]
   (update db :cart (fn [cart] (remove #(= id (:id %)) cart)))))


;; this is the same as services.add-service/show, should probably rename that to make it generic
(reg-event-fx
 :services.cart.item/edit
 [(path db/path)]
 (fn [{db :db} [_ service fields]]
   {:dispatch [:modal/show db/modal]
    :db (assoc db :adding service :form-data fields)}))


(reg-event-fx
 :services.cart.item/save-edit
 [(path db/path)]
 (fn [{db :db} _]
   (let [new-fields (:form-data db)]
     {:db (update db :cart (fn [cart] (map (fn [item] (if (= (:id item) (:id (:adding db)))
                                                      (assoc item :fields new-fields)
                                                      item)) cart)))
      :dispatch [:services.add-service/close]})))
