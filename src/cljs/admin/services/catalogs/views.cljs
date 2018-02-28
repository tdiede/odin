(ns admin.services.catalogs.views
  (:require [admin.content :as content]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [admin.services.subs]
            [re-frame.core :refer [subscribe dispatch]]
            [admin.routes :as routes]))


;; ==============================================================================
;; subview ======================================================================
;; ==============================================================================

(defn- controls [properties]
  [:div.columns
   [:div.column.is-3
    [ant/select
     {:style       {:width "100%"}
      :placeholder "select a property"}
     (map (fn [{code :code
               name :name
               id   :id}]
            (r/as-element [ant/select-option
                           {:value code
                            :key   id}
                           name]))
          properties)]]
   [:div.column.is-9.has-text-right
    [ant/button
     {:type :primary
      :icon "plus"}
     "New Service"]
    [ant/button
     {:type :primary
      :icon "plus"}
     "New Catalog"]]])

;; TODO - the out-of-the-box behavior between ant/popconfirm and ant/checkbox
;; doesn't really work for what we want to do. once we're working with data in
;; the re-frame db, i bet we can leverage some subs/dispatches to get the
;; desired behavior - that the value of the checkbox changes only after confirmation
(defn toggle-active-checkbox []
  "Wrap the checkbox for toggling a service's availability with a confirmation popup.
   That's the sort of thing that probably shouldn't change by accident"
  [ant/switch])

(defn- content [catalogs services]
  [:div.columns
   [:div.column.is-3
    [:h2 "Catalogs"]
    [ant/menu
     {:mode :vertical}
     (map #(r/as-element [ant/menu-item {:key %} %]) catalogs)]]
   [:div.column
    [:h2 "Services"]
    [ant/table
     {:dataSource services
      :scroll     {:x 100}
      :columns    [{:title     "Name"
                    :dataIndex "name"
                    :key       "name"
                    :render    #(r/as-element
                                 [:a {:href                    (routes/path-for :services/entry :service-id (aget %2 "id"))
                                      :dangerouslySetInnerHTML {:__html %1}}])}
                   {:title     (r/as-element [:span.is-pulled-right "Price"])
                    :dataIndex "price"
                    :key       "price"
                    :render    #(r/as-element [:span.is-pulled-right "$" %])}
                   {:title  (r/as-element
                             [:span "Active? "
                              [ant/tooltip {:title "Toggle availability of a service at the selected property."}
                               [ant/icon {:type "info-circle-o"}]]])
                    :render #(r/as-element [:span.is-pulled-right [toggle-active-checkbox]])}]}]]])

(defn subview []
  ;; TODO - the query that populates the `services` property only runs when the services tab is clicked. fix.
  (let [services (subscribe [:services/list]) ;; should pull from catalog items
        properties (subscribe [:properties/list])
        catalogs (r/atom ["All" "Pets" "Laundry" "Storage" "Furniture"])] ;; TODO - replace hardcoded data
    [:div
     [controls @properties]
     [content @catalogs @services]]))
