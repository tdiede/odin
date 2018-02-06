(ns onboarding.events
  (:require [ajax.core :as ajax]
            [day8.re-frame.http-fx]
            [onboarding.db :as db]
            [onboarding.prompts.events]
            [onboarding.routes :as routes]
            [re-frame.core :refer [reg-event-fx path]]
            [onboarding.chatlio]
            [toolbelt.core :as tb]
            [antizer.reagent :as ant]))

;; =============================================================================
;; Init
;; =============================================================================


(reg-event-fx
 :app/init
 (fn [{:keys [db]} _]
   {:db       db/default-value
    :dispatch [:app/bootstrap]}))


;; Fetch the server-side progress
(reg-event-fx
 :app/bootstrap
 (fn [{:keys [db]} [_ {:keys [show-loading] :or {show-loading true}}]]
   {:db         (assoc db :bootstrapping show-loading)
    :http-xhrio {:method          :get
                 :uri             "/api/onboarding"
                 :response-format (ajax/transit-response-format)
                 :on-success      [:app.bootstrap/success]
                 :on-failure      [:app.bootstrap/failure]}}))


;; On success, bootstrap the app database with server-side data
(reg-event-fx
 :app.bootstrap/success
 (fn [{:keys [db]} [_ {result :result}]]
   (let [db (db/bootstrap db result)]
     {:db            (assoc db :bootstrapping false)
      :chatlio/ready [:init-chatlio]
      :route         (routes/path-for (get-in db [:menu :active]))})))


(reg-event-fx
 :init-chatlio
 (fn [_ _]
   (let [email (aget js/window "account" "email")
         name  (aget js/window "account" "name")]
     {:chatlio/show     false
      :chatlio/identify [email {:name name}]})))


;; TODO: Update UI so that error is conveyed and option to retry is provided.
(reg-event-fx
 :app.bootstrap/failure
 (fn [{:keys [db]} [_ err]]
   (ant/notification-error {:duration    8
                            :message     "Uh oh!"
                            :description "We couldn't fetch your progress. Please check your internet connection."})))


(reg-event-fx
 :help/toggle
 (fn [_ _]
   {:chatlio/show true}))


;; =============================================================================
;; Routing
;; =============================================================================


;; Prevents flicker by checking if the application is currently being
;; bootstrapped. If so, do nothing. After bootstrap success, a `:route` effect
;; will be observed.
(reg-event-fx
 :app/route
 (fn [{:keys [db]} [_ keypath params]]
   (when-not (:bootstrapping db)
     (if (db/can-navigate-to? db keypath)
       {:db       (assoc-in db [:menu :active] keypath)
        :dispatch [:prompt/init keypath]}
       (do
         (ant/message-warning {:content "Step not available"})
         {:route (routes/path-for (get-in db [:menu :default]))})))))
