(ns admin.properties.events
  (:require [admin.properties.db :as db]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]
            [admin.routes :as routes]
            [clojure.set :as set]))


;; ==============================================================================
;; events =======================================================================
;; ==============================================================================


(reg-event-fx
 :properties/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:properties
                 [:id :name :code :cover_image_url
                  [:units [:id]]]]]
               :on-success [::properties-query k params]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::properties-query
 [(path db/path)]
 (fn [{db :db} [_ k params response]]
   (.log js/console response)
   {:dispatch [:ui/loading k false]
    :db       (->> (get-in response [:data :properties])
                   (norms/normalize db :properties/norms))}))


(reg-event-fx
 :property/fetch
 [(path db/path)]
 (fn [_ [k property-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:property {:id property-id}
                 [:id :name :code :cover_image_url
                  [:units [:id :code :name :number
                           [:rates [:id :rate :term]]
                           [:property [:id [:rates [:rate :term]]]]
                           [:occupant [:id :name
                                       [:active_license [:ends]]]]]]]]]
               :on-success [::fetch-property-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-property-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [property (get-in response [:data :property])]
     {:dispatch [:ui/loading k false]
      :db       (norms/assoc-norm db :properties/norms (:id property) property)})))


;; ==============================================================================
;; list view ====================================================================
;; ==============================================================================


(defmethod routes/dispatches :properties/list [_]
  [[:properties/query]])


;; ==============================================================================
;; entry view ===================================================================
;; ==============================================================================


(defmethod routes/dispatches :properties [{:keys [params]}]
  [[:property/fetch (tb/str->int (:property-id params))]])


(defmethod routes/dispatches :properties.entry.units/entry [{:keys [params]}]
  [[::set-unit-rates
    (tb/str->int (:property-id params))
    (tb/str->int (:unit-id params))]])


(reg-event-db
 ::set-unit-rates
 [(path db/path)]
 (fn [db [_ property-id unit-id]]
   (let [unit (db/unit db property-id unit-id)]
     (assoc-in db [:unit-rates unit-id] (db/unit-rates unit)))))


(reg-event-db
 :property.unit/update-rate
 [(path db/path)]
 (fn [db [_ unit-id rate new-rate]]
   (update-in db [:unit-rates unit-id]
              (fn [rates]
                (map
                 #(if (= (:term %) (:term rate))
                    (assoc % :rate new-rate)
                    %)
                 rates)))))


(reg-event-fx
 :property.unit.rates/update!
 [(path db/path)]
 (fn [{db :db} [k property-id unit-id]]
   (let [unit      (db/unit db property-id unit-id)
         new-rates (get-in db [:unit-rates unit-id])
         to-update (set/difference (set new-rates)
                                   (set (db/unit-rates unit)))]
     {:dispatch-n [[:ui/loading k true]
                   [::update-unit-rate! k property-id unit-id to-update]]})))


(reg-event-fx
 ::update-unit-rate!
 [(path db/path)]
 (fn [{db :db} [_ k property-id unit-id rates]]
   (let [[rate & rates] rates
         on-success     (if-not (empty? rates)
                          [::update-unit-rate! k property-id unit-id rates]
                          [::set-unit-rate-success k property-id])]
     {:graphql {:mutation
                [[:unit_set_rate {:id   unit-id
                                  :term (:term rate)
                                  :rate (float (:rate rate))}
                  [:id]]]
                :on-success on-success
                :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::set-unit-rate-success
 [(path db/path)]
 (fn [{db :db} [_ k property-id response]]
   ;; TODO: This could be more efficient
   {:dispatch-n [[:ui/loading k false]
                 [:property/fetch property-id]]}))
