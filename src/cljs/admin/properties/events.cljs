(ns admin.properties.events
  (:require [admin.properties.db :as db]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-event-fx path]]
            [toolbelt.core :as tb]
            [admin.routes :as routes]))


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
    :graphql {:query
              [[:property {:id property-id}
                [:id :name :code :cover_image_url
                 [:units [:id :code :number
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
