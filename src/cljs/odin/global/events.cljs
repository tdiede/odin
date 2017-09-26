(ns odin.global.events
  (:require [odin.global.db :as db]
            [odin.routes :as routes]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))




;; =============================================================================
;; Message Generators (Helpers)
;; =============================================================================

(defn- create-global-message
  ([key text route]
   (create-global-message text route :info))
  ([key text route level]
   {:key     key
    :text    text
    :route   route
    :level   level}))

(defn- create-rent-due-message
  [payment]
  (create-global-message :rent_due
                         [:span [:b "Your rent for this month is due."] " Go to your Membership page to make payments."]
                         :profile/membership
                         :warning))


;; =============================================================================
;; Events
;; =============================================================================


(reg-event-fx
  :global/init
  (fn [{:keys [db]} [_ config]]
    (let [account-id (get-in config [:account :id])]
      {;;:db         (assoc db :messages [])
       :dispatch-n [[::fetch-rent-payments account-id]]})))


(reg-event-fx
  ::fetch-rent-payments
  (fn [{:keys [db]} [_ account-id]]
    {:graphql {:query [[:account {:id account-id}
                        [[:active_license
                          [[:payments [:amount :status :due]]]]]]]
               :on-success [::fetch-rent-payments-success]
               :on-failure [:graphql/failure]}}))


(reg-event-db
 ::fetch-rent-payments-success
 [(path db/path)]
 (fn [db [_ resp]]
   (let [payments     (get-in resp [:data :account :active_license :payments])
         due-payments (filter #(= (:status %) :due) payments)]
     (if (< 0 (count due-payments))
       (update db :messages conj (create-rent-due-message (first due-payments)))
       db))))

;; Deletes message with provided key
(reg-event-db
 :global.messages/clear
 [(path db/path)]
 (fn [db [_ key]]
   (filter #(not= (:name %) key) (:messages db))))
