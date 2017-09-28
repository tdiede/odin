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
 (fn [{:keys [db]} [k account-id]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:payments {:params {:account (tb/str->int account-id)}}
                 [:id :method :type :autopay :amount :status :description
                  :pstart :pend :paid_on [:source [:id :name :type :last4]]]]]
               :on-success [:payments.fetch/success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 :payments.fetch/success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [payments (get-in response [:data :payments])]
     {:db       (assoc db :payments payments)
      :dispatch [:loading k false]})))
