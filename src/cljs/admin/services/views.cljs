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
            [iface.loading :as loading]
            [clojure.string :as string]))

;; ====================================================
;; service list
;; ====================================================
(defn create-service-form []
  [:div
   [ant/card
    [:h3 "Service Details"]
    [:div.columns
     [:div.column.is-6
      [ant/form-item
       {:label "Service Name"
        :type  "text"}
       [ant/input
        {:placeholder "service name"
         :on-change   #(dispatch [:service.form/update :name (.. % -target -value)])}]]
      [ant/form-item
       {:label "Description"}
       [ant/input-text-area
        {:rows        6
         :placeholder "description"
         :on-change   #(dispatch [:service.form/update :description (.. % -target -value)])}]]]
     [:div.column.is-4
      [ant/form-item
       {:label "Code"
        :type  "text"}
       [ant/input
        {:placeholder "service code"
         :on-change   #(dispatch [:service.form/update :code (.. % -target -value)])}]]
      [ant/form-item
       {:label "Catalogs"}
       [ant/select
        {:style       {:width "100%"}
         :mode        "tags"
         :placeholder "add this service to catalogs"}
        [ant/select-option {:key 1 :value :pets} "pets"]
        [ant/select-option {:key 2 :value :laundry} "laundry"]
        [ant/select-option {:key 3 :value :cleaning} "cleaning"]
        [ant/select-option {:key 4 :value :subscriptions} "subscriptions"]]]
      [ant/form-item
       {:label "Properties"}
       [ant/input
        {:style       {:width "100%"}
         :placeholder "properties"}]]]
     [:div.column.is-1
      [:div.is-pulled-right
       [ant/form-item
        {:label "Active?"}
        [ant/switch]]]]]]
   [ant/card
    [:h3"Pricing/Billing"]
    [:div.columns
     [:div.column.is-3
      [ant/form-item
       {:label "Price"}
       [ant/input-number
        {:default-value 10.00
         :style         {:width "75%"}
         :formatter     (fn [value] (str "$" value))}]]]
     [:div.column.is-3
      [ant/form-item
       {:label "Cost"}
       [ant/input-number
        {:default-value 10.00
         :style         {:width "75%"}
         :formatter     (fn [value] (str "$" value))}]]]
     [:div.column.is-3
      [ant/form-item
       {:label "Billed"}
       [ant/select
        {:style       {:width "75%"}
         :placeholder "billed"}
        [ant/select-option {:value :once} "once"]
        [ant/select-option {:value :monthly} "monthly"]]]]
     [:div.column.is-3
      [ant/form-item
       {:label "Rental?"}
       [ant/checkbox]]]]]
   [ant/card
    [:div.columns
     [:div.column.is-10
      [:h3 "Fields"]
      [:div "Information to be provided by the member when they place an order"]]
     [:div.column.is-2.is-pulled-right
      (let [menu
            [ant/menu
             [ant/menu-item "Text Box"]
             [ant/menu-item "Number"]
             [ant/menu-item "Date"]
             [ant/menu-item "Dropdown Menu"]]]
        [ant/dropdown
         {:overlay (r/as-element menu)}
         [ant/button
          "Add Field"
          [ant/icon {:type "down"}]]])]]]])

(defn create-text-input-field []
  [:div.columns
   [:div.column.is-6
    [ant/form-item
     {:label "Label"}
     [ant/input
      {:style       {:width "100%"}
       :size        "small"
       :placeholder "label or question for this input"}]]]
   [:div.column.is-1
    [ant/form-item {:label "Required?"}
     [ant/checkbox]]]
   [:div.column.is-1
    [ant/form-item {:label "Remove"}
     [ant/button
      {:shape "circle"
       :size  "small"
       :icon  "close-circle-o"
       :type  "danger"}]]]
   [:div.column.is-3
    [ant/form-item {:label "Order"}
     [ant/button {:icon "up" :type "primary"}]
     [ant/button {:icon "down" :type "primary"}]]]])

(defn create-service-modal []
  (let [form (subscribe [:services/form])]
    [ant/modal
     {:title    "Create Service"
      :width    "70%"
      :visible  @(subscribe [:modal/visible?])
      :ok-text   "Save New Service"
      :on-cancel #(dispatch [:modal/hide])
      :on-ok     #(dispatch [:service/create! @form])}

     [create-service-form]]))


(defn- render-price [_ {price :price}]
  (if (some? price) (format/currency price) "quote"))


(defn- service-filter []
  [ant/input
   {:on-change   #(dispatch [:services.search/change (.. % -target -value)])
    :placeholder "search services by name"
    :value       @(subscribe [:services/search-text])}])


(defn- controls [services]
  [:div.table-controls
   [:div.columns
    [:div.column.is-3
     [ant/form-item {:label "Filter by Service Name"}
      [service-filter]]]
    [:div.column.has-text-right
     [ant/button
      {:type     :primary
       :icon     "plus"
       :on-click #(dispatch [:modal/show])}
      "Add New Service"]]]])


(defn case-insensitive-includes? [str1 str2]
  (string/includes? (string/lower-case str1) (string/lower-case str2)))

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
                  :render    (table/wrap-cljs render-price)}]
        search-text @(subscribe [:services/search-text])]
    [ant/table
     {:columns    columns
      :dataSource (filter #(case-insensitive-includes? (:name %) search-text) services)}]))

(defn- path->selected
  [path]
  (case (vec (rest path))
    [:list]           :services
    [:orders :list]   :orders
    [:catalogs :list] :catalogs
    :services))

(defn menu [route]
  [ant/menu {:mode                  :horizontal
             :selected-keys          [(path->selected (:path route))]}
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

(defn moment->iso [instant]
  "convert a MomentJS instance to an ISO date number"
  (-> (js/moment instant)
      (.toISOString)))

(defn iso->moment [instant]
  (js/moment instant))

(defn set-range-picker
  [time-unit]
  (dispatch [:service.range/set time-unit])
  (dispatch [:service.range/close-picker]))

(defn range-picker-footer-controls
  "Some buttons to quickly select common date ranges"
  []
  [:div.columns.is-centered
   [:div.column.is-3
    [ant/button
     {:on-click #(set-range-picker "week")}
     "Past Week"]]
   [:div.column.is-3
    [ant/button
     {:on-click #(set-range-picker "month")}
     "Past Month"]]
   [:div.column.is-3
    [ant/button
     {:on-click #(set-range-picker "year")}
     "Past Year"]]])


(defn range-picker-presets
  [amount time-unit]
  [(-> (js/moment (.now js/Date))
       (.subtract amount time-unit)
       (.hour 0)
       (.minute 0)
       (.second 0))
   (-> (js/moment (.now js/Date))
       (.hour 23)
       (.minute 59)
       (.second 59))])

(defn service-detail [service]
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
     [:div
      (if-let [price (:price service)]
        (str
         "$"
         price
         (if (= :monthly (:billed service))
           "/month"
           ""))
        "Quote")]]

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
    "Ordered " (str (:order-count service) " time(s) between ")
    (let [range (subscribe [:services/range])]
      [ant/date-picker-range-picker
       {:format              "l"
        :allow-clear         false
        :ranges              {"Past Week"     (range-picker-presets 1 "week")
                              "Past Month"    (range-picker-presets 1 "month")
                              "Past 3 Months" (range-picker-presets 3 "months")
                              "Past Year"     (range-picker-presets 1 "year")}
        :value               (vec (map iso->moment @range))
        :on-change           #(dispatch [:service.range/change (moment->iso (first %)) (moment->iso (second %))])}])]])

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
