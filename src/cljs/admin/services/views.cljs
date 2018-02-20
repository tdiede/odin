(ns admin.services.views
  (:require [admin.content :as content]
            [admin.services.db :as db]
            [admin.routes :as routes]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [taoensso.timbre :as timbre]
            [iface.typography :as typography]
            [iface.utils.formatters :as format]
            [iface.components.table :as table]
            [iface.components.service :as service]
            [iface.loading :as loading]))

;; ====================================================
;; service list
;; ====================================================



;; creating a new premium service is worth some further thought.
;; one aspect of adding a service is adding that new services'
;; configuration options, which would  basically mean creating
;; a form-builder that our staff would use. this may not be a
;; good idea.

;; let's keep it simple for now, just as a placeholder.
(defn create-service-form []
  [:div
   [ant/form-item
    {:label "Service Name"
     :type "text"}
    [ant/input
     {:placeholder "Service Name"}]]
   [ant/form-item
    {:label "Description"}
    [ant/input-text-area
     {:rows 4}]]
   [ant/form-item
    {:label "Catalog"}
    [ant/select
     {:style          {:width "100%"}}
     [ant/select-option {:key 1} 1]
     [ant/select-option {:key 2} 2]
     [ant/select-option {:key 3} 3]
     [ant/select-option {:key 4} 4]
     [ant/select-option {:key 5} 5]]]
   [ant/form-item
    {:label "Price"}
    [ant/input-number
     {:defaultValue 10.00
      :formatter (fn [value] (str "$" value))}]]
   [ant/form-item
    {:label "Cost"}
    [ant/input-number
     {:defaultValue 10.00
      :formatter (fn [value] (str "$" value))}]]])

(defn create-service-modal []
  [ant/modal
   {:title    "Create Service"
    :visible  @(subscribe [:modal/visible?])
    :okText   "Save New Service"
    :onCancel #(dispatch [:modal/hide])
    ;; TODO - dispatch correct event
    :onOk     #(dispatch [:modal/hide])}

   [create-service-form]])


(defn- render-price [_ {price :price}]
  (if (some? price) (format/currency price) "quote"))


(defn- filter-by-name [services]
  [ant/select
   {:placeholder    "select service"
    :style          {:width "100%"}
    :filter-option  false
    :mode           :multiple
    :label-in-value true
    :allow-clear    true
    }
   (doall
    (for [{:keys [id name]} services]
      [ant/select-option {:key id} name]))])


(defn- controls [services]
  [:div.table-controls
   [:div.columns
    [:div.column.is-3
     [ant/form-item {:label "Filter by Service Name"}
      [filter-by-name services]]]]])


(defn services-table [services]
  (let [columns [{:title     "Name"
                  :dataIndex "name"
                  :key       "name"
                  :render    #(r/as-element
                               [:a {:href                    (routes/path-for :services/entry :service-id (.-id %2))
                                    :dangerouslySetInnerHTML {:__html %1}}])}
                 {:title     "Price"
                  :dataIndex "price"
                  :key       "price"
                  :render    (table/wrap-cljs render-price)}]]
    [ant/table
     {:columns    columns
      :dataSource services}]))


(defn service-list-main [services] ;;receives services, which is obtained from graphql
  [:div

   [create-service-modal]

   (typography/view-header "Premium Services" "Manage and view premium service offerings")
   [:div.columns
    [:div.column
     [:div.is-pulled-right
      [ant/button
       {:type     :primary
        :icon     "plus"
        :on-click #(dispatch [:modal/show])}
       "Add New Service"]]]]

   [controls services]

   [:div
    [services-table services]]])



;; =====================================================
;; route handlers
;; =====================================================

(defmethod content/view :services/list [route]
  (let [services (subscribe [:services/list])]
    [service-list-main @services]))


(defmethod content/view :services/entry [route]
  [:div
   (typography/view-header "Service Detail" "Let's Take a closer look at this here premium service.")])
