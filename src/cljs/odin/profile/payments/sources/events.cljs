(ns odin.profile.payments.sources.events
  (:require [ajax.core :as ajax]
            [odin.profile.payments.db :as db]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(reg-event-fx
 :payment.sources/set-current
 [(path db/path)]
 (fn [{:keys [db]} [_ selected-source]]
   {:db (assoc-in db [:current-source] selected-source)}))
   ;;(let [account (rand-nth (get-in db [:accounts :list]))]
   ;;  {:db      (assoc-in db [:loading :accounts/list] true)
   ;;   :graphql {:mutation   [[:set_phone {:id    (:id account)}
   ;;                           [:id :phone]]]
   ;;             :on-success [:payment.sources.load-source/success]
   ;;             :on-failure [:payment.sources.load-source/failure]}})))
