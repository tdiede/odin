(ns odin.payments.events
  (:require [re-frame.core :refer [reg-event-fx path]]
            [odin.payments.db :as db]
            [toolbelt.core :as tb]
            [odin.utils.norms :as norms]))


(reg-event-fx
 :payments/fetch
 [(path db/path)]
 (fn [{:keys [db]} [k account-id]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:payments {:params {:account (tb/str->int account-id)}}
                 [:id :method :type :autopay :amount :status :description
                  :pstart :pend :paid_on
                  [:source [:id :name :type :last4]]
                  [:account [:id]]]]]
               :on-success [:payments.fetch/success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 :payments.fetch/success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [payments (get-in response [:data :payments])]
     {:db       (norms/normalize db :payments/norms payments)
      :dispatch [:loading k false]})))
