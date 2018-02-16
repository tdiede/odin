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
   {:dispatch [:ui/loading k false]
    :db       (->> (get-in response [:data :properties])
                   (norms/normalize db :properties/norms))}))


(reg-event-fx
 :property/fetch
 [(path db/path)]
 (fn [_ [k property-id on-success]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:property {:id property-id}
                 [:id :name :code :cover_image_url :tours
                  [:rates [:rate :term]]
                  [:units [:id :code :name :number
                           [:rates [:id :rate :term]]
                           [:property [:id [:rates [:rate :term]]]]
                           [:occupant [:id :name
                                       [:active_license [:ends]]]]]]]]]
               :on-success [::fetch-property-success k on-success]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-property-success
 [(path db/path)]
 (fn [{db :db} [_ k on-success response]]
   (let [property (get-in response [:data :property])]
     {:dispatch-n (tb/conj-when [[:ui/loading k false]] on-success)
      :db         (norms/assoc-norm db :properties/norms (:id property) property)})))


;; ==============================================================================
;; list view ====================================================================
;; ==============================================================================


(defmethod routes/dispatches :properties/list [_]
  [[:properties/query]])


;; ==============================================================================
;; entry view ===================================================================
;; ==============================================================================


(defmethod routes/dispatches :properties [{:keys [params]}]
  [[:property/fetch (tb/str->int (:property-id params))
    [::set-property-rates (tb/str->int (:property-id params))]]])


(defn update-rate [term new-rate rates]
  (map
   #(if (= (:term %) term)
      (assoc % :rate new-rate)
      %)
   rates))


;; property =====================================================================


(reg-event-db
 ::set-property-rates
 [(path db/path)]
 (fn [db [_ property-id]]
   (let [property (norms/get-norm db :properties/norms property-id)]
     (assoc-in db [:property-rates property-id] (:rates property)))))


(reg-event-db
 :property/update-rate
 [(path db/path)]
 (fn [db [_ property-id rate new-rate]]
   (update-in db [:property-rates property-id]
              (partial update-rate (:term rate) new-rate))))


(reg-event-fx
 :property.rates/update!
 [(path db/path)]
 (fn [{db :db} [k property-id]]
   (let [property  (norms/get-norm db :properties/norms property-id)
         new-rates (get-in db [:property-rates property-id])
         to-update (set/difference (set new-rates)
                                   (set (:rates property)))]
     {:dispatch-n [[:ui/loading k true]
                   [::update-property-rate! k property-id to-update]]})))


(reg-event-fx
 ::update-property-rate!
 [(path db/path)]
 (fn [{db :db} [_ k property-id rates]]
   (let [[rate & rates] rates
         on-success     (if-not (empty? rates)
                          [::update-property-rate! k property-id rates]
                          [::set-rate-success k property-id])]
     {:graphql {:mutation
                [[:property_set_rate {:id   property-id
                                      :term (:term rate)
                                      :rate (float (:rate rate))}
                  [:id]]]
                :on-success on-success
                :on-failure [:graphql/failure k]}})))


(reg-event-fx
 :property.tours/toggle!
 [(path db/path)]
 (fn [_ [k property-id]]
   {:dispatch [:ui/loading k true]
    :graphql {:mutation
              [[:property_toggle_touring {:id property-id}
                [:id :tours]]]
              :on-success [::toggle-tours-success k]
              :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::toggle-tours-success
 [(path db/path)]
 (fn [{db :db} [_ k {{{:keys [id tours]} :property_toggle_touring} :data}]]
   (let [property (norms/get-norm db :properties/norms id)]
     {:db       (norms/assoc-norm db :properties/norms id (assoc property :tours tours))
      :dispatch [:ui/loading k false]})))


;; units ========================================================================


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
   (update-in db [:unit-rates unit-id] (partial update-rate (:term rate) new-rate))))


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
                          [::set-rate-success k property-id])]
     {:graphql {:mutation
                [[:unit_set_rate {:id   unit-id
                                  :term (:term rate)
                                  :rate (float (:rate rate))}
                  [:id]]]
                :on-success on-success
                :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::set-rate-success
 [(path db/path)]
 (fn [{db :db} [_ k property-id response]]
   ;; TODO: This could be more efficient
   {:dispatch-n [[:ui/loading k false]
                 [:property/fetch property-id]]}))
