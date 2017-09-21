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
   (tb/log "sub: autopay source:" source)
   (:autopay source)))


(reg-event-fx
 :payment.sources.autopay/confirm-modal
 [(path db/path)]
 (fn [_ [_ is-enabled]]
   (tb/log is-enabled)
   (if is-enabled
     {:dispatch [:modal/show :payment.source/autopay-disable]}
     {:dispatch [:modal/show :payment.source/autopay-enable]})))


(defn- get-default-source [sources]
  (first (filter #(= (:default %) true) sources)))


(reg-event-fx
 :payment.sources.autopay/enable!
 [(path db/path)]
 (fn [{:keys [db]} [_ source-id]]
   {:dispatch-n [[:loading :payment.sources.autopay/enable! true]]
    :graphql
    {:mutation   [[:set_autopay_source {:id source-id} [:id]]]
     :on-success [::autopay-enable-success]
     :on-failure [:graphql/failure]}}))


(reg-event-fx
 ::autopay-enable-success
 [(path db/path)]
 (fn [_ [_ response]]
   (let [source-id (get-in response [:data :set_autopay_source :id])]
     {:dispatch-n [[:loading :payment.sources.autopay/enable! false]
                   [:modal/hide :payment.source/autopay-enable]
                   [:notify/success "Great! Autopay is now enabled."]]
      :route      (routes/path-for :profile.payment/sources :query-params {:source-id source-id})})))


(reg-event-fx
 :payment.sources.autopay/disable!
 [(path db/path)]
 (fn [{:keys [db]} [_ source-id]]
   {:dispatch-n [[:loading :payment.sources.autopay/disable! true]]
    :graphql {:mutation   [[:unset_autopay_source {:id source-id} [:id]]]
              :on-success [::autopay-disable-success]
              :on-failure [:graphql/failure]}}))


(reg-event-fx
 ::autopay-disable-success
 [(path db/path)]
 (fn [_ _]
   {:dispatch-n [[:loading :payment.sources.autopay/disable! false]
                 [:modal/hide :payment.source/autopay-disable]]}))
