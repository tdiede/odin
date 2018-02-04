(ns iface.modules.graphql
  (:require [re-frame.core :as rf :refer [dispatch]]
            [toolbelt.core :as tb]
            [ajax.core :as ajax]
            [antizer.reagent :as ant]
            [taoensso.timbre :as timbre]
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


(defn install-module!
  [endpoint {:keys [on-unauthenticated on-error-fx]
             :or   {on-unauthenticated (constantly {}),
                    on-error-fx        (constantly {})}}]
  (rf/reg-event-fx
   ::graphql
   (fn [_ [_ {:keys [endpoint query mutation on-success on-failure]
             :or   {endpoint   endpoint
                    on-failure [:graphql/notify-errors!]}
             :as   opts}]]
     (validate-opts opts)
     (let [method (if (some? query) :get :post)]
       {:http-xhrio
        (tb/assoc-when
         {:method          method
          :uri             endpoint
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
     (timbre/error response)
     (doseq [{m :message} (get-in response [:response :errors])]
       (ant/notification-error {:message     "Unhandled Error!"
                                :description m
                                :duration    6}))))


  (rf/reg-event-fx
   :graphql/failure
   (fn [_ [_ k response]]
     (let [response (if (keyword? k) response k)]
       (timbre/error response)
       (case (:status response)
         401 (on-unauthenticated response)
         ;; 403 {:route "/logout"}
         500 (do
               (ant/notification-error {:message     "Server error!"
                                        :description "Something unexpected happened."})
               (on-error-fx [k response]))
         (do
           (doseq [{m :message} (get-in response [:response :errors])]
             (ant/notification-error {:message     "Error!"
                                      :description m
                                      :duration    6}))
           (on-error-fx [k response])))))))


;; (defn reg-graphql-handler
;;   [key interceptors gql-fn {:keys [on-success on-failure]}]
;;   (let [on-success-key (keyword (gensym))
;;         on-failure-key (if (some? on-failure)
;;                          (keyword (gensym))
;;                          :graphql/failure)]
;;     (reg-event-fx
;;      key
;;      interceptors
;;      (fn [{db :db} [_ params :as v]]
;;        {:dispatch [:loading key true]
;;         :graphql  (merge
;;                    (query-fn db v)
;;                    {:on-success [on-success-key params]
;;                     :on-failure (if (some? on-failure)
;;                                   [:graphql/failure key]
;;                                   [on-failure-key key])})}))

;;     (reg-event-fx
;;      on-success-key
;;      interceptors
;;      on-success)

;;     (when (some? on-failure)
;;       (reg-event-fx
;;        on-failure-key
;;        interceptors
;;        on-failure))))
