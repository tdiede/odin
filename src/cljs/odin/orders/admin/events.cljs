(ns odin.orders.admin.events
  (:require [odin.orders.admin.db :as db]
            [odin.utils.norms :as norms]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin/orders [{params :params}]
  (if (empty? params)
    [[:admin.orders/set-default-route]]
    [[:admin.orders/fetch (db/parse-query-params params)]]))


(reg-event-fx
 :admin.orders/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   {:route (db/params->route (:params db))}))


(defn- orders-params [{:keys [statuses from to datekey]}]
  (tb/assoc-when
   {:to      (.toISOString to)
    :from    (.toISOString from)
    :datekey datekey}
   :statuses (when-not (contains? statuses :all)
               (vec statuses))))


(reg-event-fx
 :admin.orders/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:loading k true]
    :db       (update db :params merge query-params)
    :graphql  {:query
               [[:orders {:params (orders-params query-params)}
                 [:id :price :created :quantity :name :desc :status :billed_on
                  [:account [:id :name]]
                  [:service [:id :name :code :billed :price]]
                  [:property [:id :name]]
                  [:payments [:id :amount]]]]]
               :on-success [::fetch-orders k]
               :on-failure [:graphql/failure k]}}))


(def compfns
  {:date   {:asc  #(if (and %1 %2)
                     (.isBefore (js/moment. %1) (js/moment. %2))
                     false)
            :desc #(if (and %1 %2) (.isAfter (js/moment. %1) (js/moment. %2)) false)}
   :number {:asc < :desc >}})


(defn sort-compfn
  [{:keys [sort-by sort-order] :as table}]
  (-> {:price     :number
       :created   :date
       :billed_on :date}
      (get sort-by)
      (compfns)
      (get sort-order)))


(defn- sort-orders [db orders]
  (let [table (get-in db [:params])]
    (sort-by (:sort-by table) (sort-compfn table) orders)))


(reg-event-fx
 ::fetch-orders
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db       (->> (get-in response [:data :orders])
                   (sort-orders db)
                   (norms/normalize db :orders/norms))
    :dispatch [:loading k false]}))


(reg-event-fx
 :admin.orders.status/select
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
 :admin.orders.range/change
 [(path db/path)]
 (fn [{db :db} [_ from to]]
   {:route (db/params->route (assoc (:params db) :from from :to to))}))


(reg-event-fx
 :admin.orders/datekey
 [(path db/path)]
 (fn [{db :db} [_ datekey]]
   {:route (db/params->route (assoc (:params db) :datekey datekey))}))
