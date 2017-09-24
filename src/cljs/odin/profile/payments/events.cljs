(ns odin.profile.payments.events
  (:require [odin.profile.payments.db :as db]
            [odin.profile.payments.sources.events]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Routing/Nav
;; =============================================================================


(defmethod routes/dispatches :profile.payment/history [route]
  [[:payments/fetch (get-in route [:requester :id])]])


;; =============================================================================
;; Fetch Payments
;; =============================================================================


(reg-event-fx
 :payments/fetch
 [(path db/path)]
 (fn [{:keys [db]} [_ account-id]]
   {:db      (assoc-in db [:loading :payments/list] true)
    :graphql {:query
              [[:payments {:params {:account (tb/str->int account-id)}}
                [:id :method :type :autopay :amount :status :description
                 :pstart :pend :paid_on [:source [:id :name :type :last4]]]]]
              :on-success [:payments.fetch/success]
              :on-failure [:payments.fetch/failure]}}))


(reg-event-db
 :payments.fetch/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [payments (get-in response [:data :payments])]
     (-> (assoc db :payments payments)
         (assoc-in [:loading :payments/list] false)))))


(reg-event-fx
 :payments.fetch/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :payments/list] false)
    :dispatch [:graphql/notify-errors! response]}))
