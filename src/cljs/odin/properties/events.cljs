(ns odin.properties.events
  (:require [odin.properties.db :as db]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-event-fx path]]
            [toolbelt.core :as tb]))


(reg-event-fx
 :property/fetch
 [(path db/path)]
 (fn [_ [k property-id]]
   {:dispatch [:loading k true]
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
     {:dispatch [:loading k false]
      :db       (norms/assoc-norm db :properties/norms (:id property) property)})))
