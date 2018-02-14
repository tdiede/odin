(ns admin.metrics.events
  (:require [admin.metrics.db :as db]
            [admin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :metrics [{params :params}]
  (if (empty? params)
    [[:metrics/set-default-route]]
    (let [category (-> params :view keyword)]
      [[:metrics.category/set category]
       [:metrics.category/fetch category]])))


(reg-event-fx
 :metrics/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   (let [category (:category db)]
     {:route (routes/path-for :metrics :query-params {:view (name category)})})))


(reg-event-fx
 :metrics.category/nav
 [(path db/path)]
 (fn [{db :db} [_ category]]
   {:route    (routes/path-for :metrics :query-params {:view (name category)})
    :dispatch [:metrics.category/set category]}))


(reg-event-db
 :metrics.category/set
 [(path db/path)]
 (fn [db [_ category]]
   (assoc db :category category)))


(reg-event-fx
 :metrics.category/fetch
 [(path db/path)]
 (fn [{db :db} [_ category]]
   {:dispatch [:ui/loading :metrics.category/fetch true]
    :graphql  {:query
               [[:referrals [:source]]]
               :on-success [:metrics.category.fetch/success]
               :on-failure [:graphql/failure :metrics.category/fetch]}}))


(defn transform-referrals [referrals]
  (let [total   (count referrals)
        grouped (group-by :source referrals)]
    (reduce
     (fn [acc [name v]]
       (let [c (count v)]
         (conj acc {:name  name
                    :count c
                    :y     (float (* (/ c total) 100))})))
     []
     grouped)))


(reg-event-fx
 :metrics.category.fetch/success
 [(path db/path)]
 (fn [{db :db} [_ response]]
   {:db       (->> (get-in response [:data :referrals])
                   (transform-referrals)
                   (assoc db :referrals))
    :dispatch [:ui/loading :metrics.category/fetch false]}))
