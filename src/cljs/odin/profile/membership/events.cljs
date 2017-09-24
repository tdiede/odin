(ns odin.profile.membership.events
  (:require [odin.profile.membership.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Routing
;; =============================================================================


(defmethod routes/dispatches :profile/membership [route]
  (let [account-id (get-in route [:requester :id])]
    [[:profile/fetch-account account-id]
     [:member/fetch-license account-id]]))


;; =============================================================================
;; Fetch License
;; =============================================================================


;; retrieves the `active_license` for a member.
(reg-event-fx
 :member/fetch-license
 (fn [_ [k account-id]]
   {:dispatch [:loading k true]
    :graphql  {:query      [[:account {:id account-id}
                             [[:active_license
                               [:id :rate :starts :ends :status :term
                                [:unit [:id :number]]
                                [:property [:id :name :code :cover_image_url]]
                                [:payments [:id :description :type :amount :status
                                            :due :paid_on :pstart :pend]]]]]]]
               :on-success [:member.fetch.license/success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 :member.fetch.license/success
 [(path db/path)]
 (fn [{:keys [db]} [_ k response]]
   (let [license (get-in response [:data :account :active_license])]
     {:db       (assoc db :license license)
      :dispatch [:loading k false]})))


;; =============================================================================
;; Make Payment
;; =============================================================================


(reg-event-fx
 :member/pay-rent-payment!
 (fn [_ [k payment-id source-id]]
   {:dispatch [:loading k true]
    :graphql  {:mutation   [[:pay_rent_payment {:id     payment-id
                                                :source source-id}
                             [:id]]]
               :on-success [::make-payment-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::make-payment-success
 (fn [{db :db} [_ k _]]
   (let [account-id (get-in db [:config :account :id])]
     {:dispatch-n [[:loading k false]
                   [:member/fetch-license account-id]]})))
