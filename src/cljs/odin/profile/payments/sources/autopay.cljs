(ns odin.profile.payments.sources.autopay
  (:require [odin.profile.payments.sources.db :as db]
            [odin.components.modals]
            [odin.routes :as routes]
            [reagent.ratom :as r :refer-macros [reaction]]
            [re-frame.core :refer [reg-sub
                                   subscribe
                                   reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))

(reg-sub
 ::sources
 (fn [db _]
   (db/path db)))


(defn get-autopay-source
  [sources]
  (first (filter #(true? (:autopay %)) sources)))

(defn has-autopay-source
  [sources]
  (let [has (empty? (filter #(true? (:autopay %)) sources))]
    ;;(println "has autopay? " has (filter #(true? (:autopay %)) sources))
    has))



;; Returns currently selected Autopay source, if it exists.
(reg-sub
 :payment.sources/autopay-source
 :<- [:payment/sources]
 (fn [sources _]
   (tb/find-by :autopay sources)))


(reg-sub
 :payment.sources/autopay-on?
 :<- [:payment.sources/autopay-source]
 (fn [source _]
   (:autopay source)))


(reg-event-fx
 :payment.sources.autopay/confirm-modal
 [(path db/path)]
 (fn [_ [_ is-enabled]]
   (if is-enabled
     {:dispatch [:modal/show :payment.source/autopay-disable]}
     {:dispatch [:modal/show :payment.source/autopay-enable]})))


;;(reg-event-fx
;; :payment.sources.autopay/disable!
;; [(path db/path)]
;; (fn [{:keys [db]} _]
;;   (let [id (:id @(subscribe [:payment.sources/autopay-source]))]
;;     (tb/log id)
;;     {:db      (assoc-in db [:loading :list] true)
;;      :graphql {:mutation   [[:unset_autopay_source {:id id} [:id]]]
;;                :on-success [:payment.sources/fetch]
;;                :on-failure [:payment.sources/fetch]}})))

(defn- get-default-source [sources]
  (first (filter #(= (:default %) true) sources)))


(reg-event-fx
 :payment.sources.autopay/enable!
 [(path db/path)]
 (fn [{:keys [db]} [_ source-id]]
   {:graphql {:mutation   [[:set_autopay_source {:id source-id} [:id]]]
              :on-success [:payment.sources/fetch]
              :on-failure [:graphql/failure]}}))


;;[:notify/success "Great! Autopay is now enabled."]

(reg-event-db
 :payment.sources.autopay/toggle!
 [(path db/path)]
 (fn [db [_ value]]
    ;;(let [sources (subscribe [:payment.sources/autopay-sources])
          ;;active  (subscribe [:payment.sources/autopay-source])]
   ;;(if (true? value))


   db))
      ;;(if (true? value)
        ;;(map disable-autopay sources))))
        ;;(map enable-autopay sources))))
