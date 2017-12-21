(ns odin.payment-sources.events
  (:require [odin.payment-sources.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [path reg-event-db reg-event-fx]]
            [toolbelt.core :as tb]
            [odin.utils.norms :as norms]))


(reg-event-fx
 :payment-sources/fetch
 (fn [{:keys [db]} [k account-id opts]]
   {:dispatch [:loading k true]
    :graphql  {:query
               [[:payment_sources {:account account-id}
                 [:id :last4 :customer :type :name :default :status :autopay :expires
                  [:payments [:id :method :type :autopay :amount :status :pstart :pend :paid_on :description]]
                  [:account [:id]]]]]
               :on-success [::fetch-success k account-id opts]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-success
 [(path db/path)]
 (fn [{:keys [db]} [_ k account-id {:keys [on-success]} response]]
   (let [sources    (vec (get-in response [:data :payment_sources]))
         on-success (when-some [v on-success] (conj v sources))]
     {:dispatch-n (tb/conj-when [[:loading k false]] on-success)
      :db         (norms/normalize db :payment-sources/norms sources)})))
