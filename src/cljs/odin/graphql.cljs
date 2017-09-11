(ns odin.graphql
  (:require [re-frame.core :as rf :refer [dispatch]]
            [toolbelt.core :as tb]
            [ajax.core :as ajax]
            [antizer.reagent :as ant]
            [venia.core :as venia]))



(rf/reg-fx
 :graphql
 (fn [opts]
   (dispatch [::graphql opts])))


(defn- validate-opts [{:keys [query mutation on-success]}]
  (assert (some? on-success) "An `:on-success` handler must be supplied!")
  (assert (vector? on-success) "`:on-success` must be an event vector.")
  (assert (or (some? query) (some? mutation)) "A query or mutation must be supplied!")
  (assert (not (and (some? query) (some? mutation))) "Cannot perform query and mutation simultaneously!"))


(defn- ->graphql [x]
  (venia/graphql-query {:venia/queries x}))


(rf/reg-event-fx
 ::graphql
 (fn [_ [_ {:keys [query mutation on-success on-failure]
           :or   {on-failure [:graphql/notify-errors!]}
           :as   opts}]]
   (validate-opts opts)
   (let [method (if (some? query) :get :post)]
     {:http-xhrio
      (tb/assoc-when
       {:method          method
        :uri             "/api/graphql"
        :params          (tb/assoc-when
                          {}
                          :query (when-some [q query] (->graphql q))
                          :mutation (when-some [m mutation] (->graphql m)))
        :response-format (ajax/transit-response-format)
        :on-success      on-success
        :on-failure      on-failure}
       :format (when (#{:post} method) (ajax/transit-request-format)))})))



(rf/reg-event-fx
 :graphql/notify-errors!
 (fn [_ [_ response]]
   (tb/error response)
   (doseq [{m :message} (get-in response [:response :errors])]
     (ant/notification-error {:message     "Unhandled Error!"
                              :description m
                              :duration    6}))))


(rf/reg-event-fx
 :graphql/failure
 (fn [_ [_ k response]]
   (tb/error k response)
   (case (:status response)
     401 {:route "/logout"}
     500 (ant/notification-error {:message     "Server error!"
                                 :description "Shit!"})
     (do
       (doseq [{m :message} (get-in response [:response :errors])]
         (ant/notification-error {:message     "Unhandled Error!"
                                  :description m
                                  :duration    6}))
       {:dispatch [:loading k false]}))))
