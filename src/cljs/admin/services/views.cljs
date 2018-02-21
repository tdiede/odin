(ns admin.services.views
  (:require [admin.content :as content]
            [admin.services.db :as db]
            [admin.routes :as routes]
            [admin.services.orders.views :as orders-views]
            [admin.services.catalogs.views :as catalogs-views]
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
     {:default-value 10.00
      :formatter (fn [value] (str "$" value))}]]
   [ant/form-item
    {:label "Cost"}
    [ant/input-number
     {:default-value 10.00
      :formatter (fn [value] (str "$" value))}]]])

(defn create-service-modal []
  [ant/modal
   {:title    "Create Service"
    :visible  @(subscribe [:modal/visible?])
    :ok-text   "Save New Service"
    :on-cancel #(dispatch [:modal/hide])
    ;; TODO - dispatch correct event
    :on-ok     #(dispatch [:modal/hide])}

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
      [filter-by-name services]]]
    [:div.column.has-text-right
     [ant/button
      {:type     :primary
       :icon     "plus"
       :on-click #(dispatch [:modal/show])}
      "Add New Service"]]]])


(defn services-table [services]
  (let [columns [{:title     "Name"
                  :dataIndex "name"
                  :key       "name"
                  :render    #(r/as-element
                               [:a {:href                    (routes/path-for :services/entry :service-id (aget %2 "id"))
                                    :dangerouslySetInnerHTML {:__html %1}}])}
                 {:title     "Price"
                  :dataIndex "price"
                  :key       "price"
                  :render    (table/wrap-cljs render-price)}]]
    [ant/table
     {:columns    columns
      :dataSource services}]))

(defn- path->selected
  [path]
  (case (vec (rest path))
    [:list]           :services
    [:orders :list]   :orders
    [:catalogs :list] :catalogs
    :services))

(defn menu [route]
  [ant/menu {:mode                  :horizontal
             :selected-key          [(path->selected (:path route))]}
   [ant/menu-item {:key :services}
    [:a {:href (routes/path-for :services/list)}
     "Services"]]
   [ant/menu-item {:key :orders}
    [:a {:href (routes/path-for :services.orders/list)}
     "Orders"]]
   [ant/menu-item {:key :catalogs}
    [:a {:href (routes/path-for :services.catalogs/list)}
     "Catalogs"]]])

(defn services-subview
  []
  (let [services (subscribe [:services/list])]
    [:div
     [controls @services]
     [services-table @services]]))


(defn service-layout [route] ;;receives services, which is obtained from graphql
  [:div

   [create-service-modal]

   (typography/view-header "Premium Services" "Manage and view premium service offerings")

   [:div.mb2
    [menu route]]

   ;; render subviews based on the active menu item
   (case (:page route)
     :services/list          [services-subview]
     :services.orders/list   [orders-views/subview]
     :services.catalogs/list [catalogs-views/subview])])



;; =====================================================
;; service entry (detail view)
;; =====================================================

(defn service-detail [service]
  (js/console.log service)
  [:div
   ;; header and controls
   [:div.columns
    [:div.column.is-9
     (typography/view-header (:name service) (:desc service))]
    [:div.column.is-3.is-pulled-right
     [ant/button "Delete"]
     [ant/button "Edit"]]]

   ;; content detail
   [:div.columns
    [:div.column.is-3
     "Price"
     [:div (str
            "$"
            (:price service)
            (if (= :monthly (:billed service))
              "/month"
              ""))]]

    [:div.column.is-3
     "Cost"
     [:div
      (if (nil? (:cost service))
       "n/a"
       (str "$" (:cost service)))]]

    [:div.column.is-3
     "Margin"
     [:div
      (if (nil? (:cost service))
        "n/a"
        (str "$" (- (:price service) (:cost service))))]]]
   [:div
    "Ordered " (str (:order-count service) " time(s)")]]) ;;TODO - get this info out of graphql

(defn service-detail-main
  [{{service-id :service-id} :params}]
  (let [service (subscribe [:service (tb/str->int service-id)])]
    [service-detail @service]))



;; =====================================================
;; route handlers
;; =====================================================

;; services list
(defmethod content/view :services [route]
  [service-layout route])

;; services entry
(defmethod content/view :services/entry [route]
  [service-detail-main route])
