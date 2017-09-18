(ns odin.profile.membership.events
  (:require [odin.profile.membership.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :profile/membership [route]
  (let [account-id (get-in route [:requester :id])]
   [[:profile/fetch-account account-id]
    [:member/fetch-license account-id]]))

;; Retrieves the `active_license` for a Member.
(reg-event-fx
 :member/fetch-license
 [(path db/path)]
 (fn [{:keys [db]} [_ account-id]]
    {:db      (assoc-in db [:loading :member/license] true)
     :graphql {:query [[:account {:id account-id}
                        [[:active_license
                          [:id :rate :starts :ends :status :term
                           [:unit [:id :number]]
                           [:property [:id :name :code :cover_image_url]]
                           [:payments [:amount :status :due :paid_on :pstart :pend]]]]]]]
               :on-success [:member.fetch.license/success]
               :on-failure [:member.fetch.license/failure]}}))


(reg-event-db
 :member.fetch.license/success
 [(path db/path)]
 (fn [db [_ response]]
   (let [license (get-in response [:data :account :active_license])]
     (-> (assoc db :license license)
         (assoc-in [:loading :member/license] false)))))


(reg-event-fx
 :member.fetch.license/failure
 [(path db/path)]
 (fn [{:keys [db]} [_ response]]
   {:db       (assoc-in db [:loading :member/license] false)
    :dispatch [:graphql/notify-errors! response]}))
