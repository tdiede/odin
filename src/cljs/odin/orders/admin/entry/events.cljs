(ns odin.orders.admin.entry.events
  (:require [odin.routes :as routes]
            [re-frame.core :refer [reg-event-fx reg-event-db path]]
            [toolbelt.core :as tb]
            [odin.orders.db :as db]))


(defmethod routes/dispatches :admin.orders/entry [route]
  [[:order/fetch (tb/str->int (get-in route [:params :order-id]))]])


;; TODO: Show ant message
(reg-event-fx
 :order/refresh
 (fn [_ [_ order-id]]
   {:dispatch [:order/fetch order-id]}))


(reg-event-fx
 :admin.order/place!
 (fn [_ [k {id :id} {:keys [send-notification projected-fulfillment]}]]
   {:dispatch [:loading k true]
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
   {:dispatch-n [[:loading k false]
                 [:order/fetch (get-in response [:data :place_order :id])]
                 [:modal/hide :admin.order/place]]}))


(reg-event-fx
 :admin.order/cancel!
 (fn [_ [k {id :id} {:keys [send-notification]}]]
   {:dispatch [:loading k true]
    :graphql {:mutation
              [[:cancel_order {:id id :notify (boolean send-notification)} [:id]]]
              :on-success [::cancel! k]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::cancel!
 (fn [_ [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:order/fetch (get-in response [:data :cancel_order :id])]
                 [:modal/hide :admin.order/cancel]]}))


(reg-event-fx
 :admin.order/fulfill!
 (fn [_ [k {id :id} {:keys [send-notification actual-fulfillment process-charge]}]]
   (tb/assoc-when
    {:dispatch [:loading k true]
     :graphql  {:mutation
                [[:fulfill_order {:id           id
                                  :fulfilled_on (.toISOString actual-fulfillment)
                                  :charge       (boolean process-charge)
                                  :notify       (boolean send-notification)}
                  [:id]]]
                :on-success [::fulfill! k]
                :on-failure [:graphql/failure k]}}
    :dispatch-later (when process-charge
                      [{:ms      3000
                        :dispatch [:order/fetch id]}]))))


(reg-event-fx
 ::fulfill!
 (fn [_ [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:order/fetch (get-in response [:data :fulfill_order :id])]
                 [:modal/hide :admin.order/fulfill]]}))


(reg-event-fx
 :admin.order/charge!
 (fn [_ [k {id :id}]]
   {:dispatch       [:loading k true]
    :graphql        {:mutation
                     [[:charge_order {:id id} [:id]]]
                     :on-success [::charge! k]
                     :on-failure [:graphql/failure k]}
    :dispatch-later [{:ms       3000
                      :dispatch [:order/fetch id]}]}))


(reg-event-fx
 ::charge!
 (fn [_ [_ k response]]
   {:dispatch-n [[:loading k false]
                 [:order/fetch (get-in response [:data :charge_order :id])]
                 [:modal/hide :admin.order/charge]]}))


(reg-event-db
 :admin.order/editing
 [(path db/path)]
 (fn [db [_ order-id is-editing]]
   (assoc-in db [:admin.order/editing order-id] is-editing)))


(reg-event-fx
 :admin.order/update!
 (fn [_ [k order params]]
   (let [uparams (reduce
                  (fn [acc [k v]]
                    (if (not= v (get order k))
                      (assoc acc k v)
                      acc))
                  {}
                  params)]
     {:dispatch [:loading k true]
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
     {:dispatch-n [[:loading k false]
                   [:order/fetch order-id]
                   [:admin.order/editing order-id false]]})))
