(ns odin.global.events
  (:require [odin.global.db :as db]
            [odin.routes :as routes]
            [reagent.core :as r]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]
            [odin.utils.time :as t]))




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


(defn- rent-due-message [{due :due}]
  (let [is-overdue (.isAfter (js/moment.) (js/moment. due))]
    (create-global-message :rent-due
                           [:span
                            [:b (format/format "Your rent for this month is %s"
                                               (if is-overdue "overdue!" "due."))]
                            " Go to your " [:u "Membership"] " page to pay your rent."]
                           :profile/membership
                           (if is-overdue :danger :warning))))


(defn- deposit-overdue-message [{due :due}]
  (let [is-overdue (.isAfter (js/moment.) (js/moment. due))]
    (create-global-message :deposit-overdue
                           [:span
                            [:b "Your security deposit is overdue!"]
                            " Go to your " [:u "Membership"] " page to pay your deposit."]
                           :profile/membership
                           :danger)))


;; =============================================================================
;; Events
;; =============================================================================


(reg-event-fx
 :global/init
 (fn [{:keys [db]} [_ config]]
   (let [account-id (get-in config [:account :id])]
     {:dispatch-n [[::fetch account-id]]})))


(reg-event-fx
 ::fetch
 (fn [{:keys [db]} [_ account-id]]
   {:graphql {:query      [[:account {:id account-id}
                            [[:deposit [:id :due :amount :amount_remaining :amount_paid :amount_pending]]
                             [:active_license [[:payments [:amount :status :due]]]]]]]
              :on-success [::fetch-success]
              :on-failure [:graphql/failure]}}))


(defn- messages [{:keys [account] :as data}]
  (let [due-payments (->> (get-in account [:active_license :payments])
                          (filter #(= (:status %) :due)))
        deposit      (:deposit account)]
    (cond-> []
      (not (empty? due-payments))
      (conj (rent-due-message (first due-payments)))

      (and (> (:amount_remaining deposit) 0) (t/is-before-now (:due deposit)))
      (conj (deposit-overdue-message deposit)))))


(reg-event-db
 ::fetch-success
 [(path db/path)]
 (fn [db [_ response]]
   (tb/log response)
   (update db :messages concat (messages (:data response)))))


(reg-event-db
 :global.messages/clear
 [(path db/path)]
 (fn [db [_ key]]
   (update db :messages (fn [ms] (filter #(not= (:key %) key) ms)))))
