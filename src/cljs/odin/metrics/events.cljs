(ns odin.metrics.events
  (:require [odin.metrics.db :as db]
            [odin.routes :as routes]
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
   {:dispatch [:loading :metrics.category/fetch true]
    :graphql  {:query
               [[:referrals
                 [:percentage :count :label]]]
               :on-success [:metrics.category.fetch/success]
               :on-failure [:graphql/failure :metrics.category/fetch]}}))


(defn transform-referral
  [{:keys [percentage label count]}]
  {:name  label
   :count count
   :y     percentage})


(defn transform-metrics [metrics]
  (tb/transform-when-key-exists
      metrics
      {:referrals (partial map transform-referral)}))


(reg-event-fx
 :metrics.category.fetch/success
 [(path db/path)]
 (fn [{db :db} [_ response]]
   {:db       (-> (:data response) transform-metrics (merge db))
    :dispatch [:loading :metrics.category/fetch false]}))
