(ns admin.services.views
  (:require [admin.content :as content]
            [admin.services.db :as db]
            [admin.routes :as routes]
            [admin.services.orders.views :as orders-views]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [taoensso.timbre :as timbre]
            [iface.components.typography :as typography]
            [iface.utils.formatters :as format]
            [iface.components.table :as table]
            [iface.components.service :as service]
            [iface.loading :as loading]
            [clojure.string :as string]))


;; ==============================================================================
;; some range helpers what probably should go in iface dot components i guess ===
;; ==============================================================================



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






;; ==============================================================================
;; create service form ==========================================================
;; ==============================================================================


(defn service-field-type
  [index type]
  [ant/form-item
   {:label     (when (zero? index) "Type")
    :read-only true}
   (clojure.core/name type)])

(defn service-field-label
  [index label]
  [ant/form-item
   {:label (when (zero? index) "Label")}
   [ant/input
    {:style       {:width "100%"}
     :placeholder "label or question for this input"
     :value       label
     :on-change   #(dispatch [:service.form.field/update index :label (.. % -target -value)])}]])


(defn service-field-required
  [index required]
  [ant/form-item
   {:label (when (zero? index) "Required?")}
   [ant/switch
    {:checked required
     :on-change #(dispatch [:service.form.field/update index :required %])}]])


(defn service-field-remove
  [index]
  [ant/form-item
   {:label (when (zero? index) "Remove")}
   [ant/button
    {:shape    "circle"
     :icon     "close-circle-o"
     :type     "danger"
     :on-click #(dispatch [:service.form.field/delete index])}]])


(defn service-field-order
  [index]
  [ant/form-item
   {:label (when (zero? index) "Order")}
   [ant/button-group
    [ant/button
     {:icon     "up"
      :type     "primary"
      :on-click #(dispatch [:service.form.field/reorder index (dec index)])
      :disabled (zero? index)}]
    [ant/button
     {:icon     "down"
      :disabled @(subscribe [:services.form.field/is-last? index])
      :on-click #(dispatch [:service.form.field/reorder index (inc index)])
      :type     "primary"}]]])


(defn service-field-options-entry
  [{:keys [field_index index value]}]
  [:div.columns {:key index}
   [:div.column.is-8
    [ant/input
     {:placeholder "label"
      :default-value       value
      :on-change   #(dispatch [:service.form.field.option/update field_index index (.. % -target -value)])}]]
   [:div.column.is-1
    [ant/button
     {:icon     "close-circle-o"
      :shape    "circle"
      :type     "danger"
      :on-click #(dispatch [:service.form.field.option/delete field_index index])}]]
   [:div.column.is-3
    [ant/button-group
     [ant/button
      {:icon     "up"
       :type     "primary"
       :disabled (zero? index)
       :on-click #(dispatch [:service.form.field.option/reorder field_index index (dec index)])}]
     [ant/button
      {:icon     "down"
       :type     "primary"
       :disabled @(subscribe [:services.form.field.option/is-last? field_index index])
       :on-click #(dispatch [:service.form.field.option/reorder field_index index (inc index)])}]]]])


(defn service-field-options-popover
  [index options]
  [:div
   (doall (map service-field-options-entry options))
   [:div.columns
    [:div.column.is-6.is-offset-3
     [ant/button
      {:style    {:width "100%"}
       :on-click #(dispatch [:service.form.field.option/create index])}
      "Add Option"]]]])

(defn service-field-options-container
  [index options]
  [ant/form-item
   {:label (when (zero? index) "Options")}
   [ant/popover
    {:title         "Menu Options"
     :overlay-style {:width "30%"}
     :content       (r/as-element [service-field-options-popover index options])}
    [ant/button
     {:style {:width "100%"}}
     (str "Menu Options (" (count options) ")")]]])

(defmulti render-service-field :type)


(defmethod render-service-field :dropdown
  [{:keys [index label required type options]}]

  [:div.columns {:key index}
   [:div.column.is-1
    [service-field-type index type]]
   [:div.column.is-5
    [service-field-label index label]]
   [:div.column.is-3
    [service-field-options-container index options]]
   [:div.column.is-1
    [service-field-required index required]]
   [:div.column.is-1
    [service-field-remove index]]
   [:div.column.is-2
    [service-field-order index]]])


(defmethod render-service-field :default
  [{:keys [index label required type]}]
  [:div.columns {:key index}
   [:div.column.is-1
    [service-field-type index type]]
   [:div.column.is-8
    [service-field-label index label]]
   [:div.column.is-1
    [service-field-required index required]]
   [:div.column.is-1
    [service-field-remove index]]
   [:div.column.is-2
    [service-field-order index]]])


(defn add-fields-menu []
  (let [menu
        [ant/menu {:on-click #(dispatch [:service.form.field/create (aget % "key")])}
         [ant/menu-item {:key "text" }"Text Box"]
         [ant/menu-item {:key "number"} "Number"]
         [ant/menu-item {:key "date"} "Date"]
         [ant/menu-item {:key "time"} "Time"]
         [ant/menu-item {:key "dropdown"} "Dropdown Menu"]]]
    [ant/dropdown
     {:overlay (r/as-element menu)}
     [ant/button
      "Add Field"
      [ant/icon {:type "down"}]]]))


(defn fields-card [fields]
  [ant/card {:title "Fields" :extra (r/as-element [add-fields-menu])}
   (doall (map render-service-field fields))])


(defn create-service-form []
  (let [form     (subscribe [:services/form])
        catalogs (subscribe [:services/catalogs])]
    [:div
     [ant/card {:title "Service Details"}
      [:div.columns
       [:div.column.is-6
        [ant/form-item
         {:label "Service Name"
          :type  "text"}
         [ant/input
          {:placeholder "service name"
           :value       (:name @form)
           :on-change   #(dispatch [:service.form/update :name (.. % -target -value)])}]]
        [ant/form-item
         {:label "Description"}
         [ant/input-text-area
          {:rows        6
           :placeholder "description"
           :value       (:description @form)
           :on-change   #(dispatch [:service.form/update :description (.. % -target -value)])}]]]
       [:div.column.is-4
        [ant/form-item
         {:label "Code"
          :type  "text"}
         [ant/input
          {:placeholder "service code"
           :value       (:code @form)
           :on-change   #(dispatch [:service.form/update :code (.. % -target -value)])}]]
        [ant/form-item ;; TODO - make this all dynamic.
         {:label "Catalogs"}
         [ant/select
          {:style       {:width "100%"}
           :mode        "tags"
           :value       (:catalogs @form)
           :on-change   #(dispatch [:service.form/update :catalogs (js->clj %)])
           :placeholder "add this service to catalogs"}
          (map-indexed (fn [i catalog]
                         [ant/select-option
                          {:key   i
                           :value catalog}
                          (clojure.core/name catalog)])
                       @catalogs)]]
        [ant/form-item
         {:label "Properties"}
         [ant/select
          {:style       {:width "100%"}
           :placeholder "select properties"
           :mode        "multiple"
           :value       (mapv str (:properties @form))
           :on-change   #(let [ids (mapv tb/str->int (js->clj %))]
                           (dispatch [:service.form/update :properties ids]))}
          (map (fn [{:keys [name id]}]
                 [ant/select-option
                  {:value (str id)
                   :key   id}
                  name])
               @(subscribe [:properties/list]))]]]
       [:div.column.is-1
        [:div.is-pulled-right
         [ant/form-item
          {:label "Active?"}
          [ant/switch
           {:checked (:active @form)
            :on-change #(dispatch [:service.form/update :active %])}]]]]]]

     [ant/card {:title "Pricing/Billing"}
      [:div.columns
       [:div.column.is-3
        [ant/form-item
         {:label "Price"}
         [ant/input-number
          {:value     (:price @form)
           :style     {:width "75%"}
           :formatter (fn [value] (str "$" value))
           :on-change #(dispatch [:service.form/update :price %])}]]]
       [:div.column.is-3
        [ant/form-item
         {:label "Cost"}
         [ant/input-number
          {:value     (:cost @form)
           :style     {:width "75%"}
           :formatter (fn [value] (str "$" value))
           :on-change #(dispatch [:service.form/update :cost %])}]]]
       [:div.column.is-3
        [ant/form-item
         {:label "Billed"}
         [ant/select
          {:style       {:width "75%"}
           :placeholder "billed"
           :value       (:billed @form)
           :on-change   #(dispatch [:service.form/update :billed (keyword %)])}
          [ant/select-option {:value :once} "once"]
          [ant/select-option {:value :monthly} "monthly"]]]]
       [:div.column.is-3
        [ant/form-item
         {:label "Rental?"}
         [ant/checkbox
          {:checked   (:rental @form)
           :on-change #(dispatch [:service.form/update :rental (.. % -target -checked)])}]]]]]

     [fields-card (:fields @form)]]))


(defn create-service-modal []
  (let [form (subscribe [:services/form])]
    [ant/modal
     {:title       "Create Service"
      :width       "70%"
      :visible     @(subscribe [:modal/visible? :service/create-service-form])
      :ok-text     "Save New Service"
      :on-cancel   #(dispatch [:service.form/hide])
      :on-ok       (fn []
                     (let [{name        :name
                            description :description
                            code        :code} @form]
                       (if (every? #(empty? %) [name description code])
                         (ant/notification-error
                          {:message     "Additional information required"
                           :description "Please enter a name, description, and code for this service."})
                         (dispatch [:service/create! @form]))))
      :after-close #(dispatch [:service.form/clear])}

     [create-service-form]]))



;; ==============================================================================
;; services list ================================================================
;; ==============================================================================



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
       :on-click #(dispatch [:service.form/show])}
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
      :pagination {:position :top}
      :dataSource (filter #(case-insensitive-includes? (:name %) search-text) services)}]))

(defn- path->selected
  [path]
  (case (vec (rest path))
    [:list]           :services
    [:orders :list]   :orders
    :services))

(defn menu [route]
  [ant/menu {:mode                  :horizontal
             :selected-keys          [(path->selected (:path route))]}
   [ant/menu-item {:key :services}
    [:a {:href (routes/path-for :services/list)}
     "Service Offerings"]]
   [ant/menu-item {:key :orders}
    [:a {:href (routes/path-for :services.orders/list)}
     "Manage Orders"]]])


(defn- services-list [services]
  (let [columns     [{:title     "Name"
                      :dataIndex "name"
                      :key       "name"
                      :render    #(r/as-element
                                   [:a {:href                    (routes/path-for :services/entry :service-id (aget %2 "id"))
                                        :dangerouslySetInnerHTML {:__html %1}}])
                      }]
        search-text @(subscribe [:services/search-text])]
    [ant/table
     {:columns     columns
      :show-header false
      :dataSource  (filter #(case-insensitive-includes? (:name %) search-text) services)}]))


;; =====================================================
;; service entry (detail view)
;; =====================================================


(defn- service-entry-field [{:keys [id index type label required options]}]
  [:div.columns {:key id}
   [:div.column.is-1
    [:p [:b "Type"]]
    [:div (clojure.core/name type)]]

   [:div.column.is-9
    [:p [:b "Label"]]
    [:p label]]

   [:div.column.is-1
    [:p [:b "Required?"]]
    [ant/switch {:checked required}]]

   (when (and (= :dropdown type) (not (empty? options)))
     [:div.column [:b "Options: "]
      (map (fn [option]
             [:span {:key (:label option)} (:label option)]) options)])])


(defn- service-entry [service]
  (let [{:keys [id name description code active price cost billed rental catalogs properties order-count fields]} @service]
    [:div
     [:div.mb2
      [:div
       [ant/button
        {:on-click #(dispatch [:service/edit-service @service])}
        "Edit"]
       [ant/popconfirm
        {:title      (r/as-element [:div
                                    [:h4 "Are you sure you want to delete this service offering?"]
                                    [:div "This action can't be undone. Perhaps you want to "
                                     [:strong "deactivate"]
                                     " this service instead?"]])
         :ok-text    "Yes, delete this service"
         :ok-type    :danger
         :on-confirm #(dispatch [:service/delete! (:id @service)])}
        [ant/button
         "Delete"]]
       [ant/button
        {:on-click #(dispatch [:service/copy-service @service])}
        "Make a Copy"]]]
     [ant/card
      {:title "Service Details"}
      [:div.columns
       [:div.column.is-6
        [:h3 [:b name]]
        [:p description]]
       [:div.column.is-4
        [:div.mb1
         [:p [:b "Code"]]
         [:p code]]

        [:div.mb1
         [:p [:b "Catalogs"]]
         (if (nil? catalogs)
           [:p "none"]
           [:p (map-indexed
               (fn [i catalog]
                 (if (zero? i)
                   (str (clojure.core/name catalog))
                   (str ", " (clojure.core/name catalog))))
               catalogs)])]

        [:div.mb1
         [:p [:b "Properties"]]
         (if (empty? properties)
           [:p "none"]
           [:p (map-indexed
                (fn [i property]
                  (let [property-name (:name  @(subscribe [:property property]))]
                    (if (zero? i)
                      property-name
                      (str ", " property-name))))
                properties)])]]

       [:div.column.is-2
        [:p.mb1 [:b "Active?"]]
        [ant/switch {:checked active}]]]]

     [ant/card {:title "Pricing/Billing"}
      [:div.columns
       [:div.column.is-3
        [:div
         [:p [:b "Price"]]
         (if (nil? price)
           [:p "Quote"]
           [:p (str
                "$"
                price
                (if (= :monthly billed)
                  "/month"
                  ""))])]]
       [:div.column.is-3
        [:div
         [:p [:b "Cost"]]
         (if (nil? cost)
           [:p "n/a"]
           [:p (str "$" cost)])]]

       [:div.column.is-3
        [:div
         [:p [:b "Billed"]]
         [:p billed]]]

       [:div.column.is-3
        [:div
         [:p [:b "Rental?"]]
         [ant/checkbox {:checked rental}]]]]]

     [ant/card {:title "Metrics"}
      [:p [:b "Usage"]]
      [:p
       "Ordered " (str order-count " time(s) between ")
       (let [range (subscribe [:services/range])]
         [ant/date-picker-range-picker
          {:format      "l"
           :allow-clear false
           :ranges      {"Past Week"     (range-picker-presets 1 "week")
                         "Past Month"    (range-picker-presets 1 "month")
                         "Past 3 Months" (range-picker-presets 3 "months")
                         "Past Year"     (range-picker-presets 1 "year")}
           :value       (vec (map iso->moment @range))
           :on-change   #(dispatch [:service.range/change (moment->iso (first %)) (moment->iso (second %))])}])]]

     [ant/card {:title "Fields" :extra "Information to be provided by the member when they place an order."}
      (if (nil? fields)
        [:p "No fields found."]

        [:div
         (map
          (fn [field]
            [service-entry-field field])
          fields)])]]))


(defn services-list-container [services]
  [:div.column.is-3
   [:div.mb2
    [ant/button
     {:style {:width "100%"}
      :type  :primary
      :icon  "plus"
      :on-click #(dispatch [:service.form/show])}
     "Create a New Service"]]
   [:div.mb1
    [service-filter]]
   [services-list @services]])


(defn services-entry-container [route]
  (if (not (nil? (get-in route [:params :service-id])))
    (when-let [service (subscribe [:service (tb/str->int (get-in route [:params :service-id]))])]
      [:div.column.is-9
       [service-entry service]])
    [:p.mt2.ml2
     "Select a service from the list to see more details."]))

(defn services-editing-container [route]
  (let [service-id (get-in route [:params :service-id])]
    [:div.column.is-9
     [:div.mb2
      [:div
       [ant/button
        {:on-click #(dispatch [:service/cancel-edit])}
        "Cancel"]
       [ant/button "Save Changes"]]]
     [create-service-form]]))

(defn services-subview
  [route]
  (let [services (subscribe [:services/list])]
    [:div.columns
     [services-list-container services]
     (if @(subscribe [:services/is-editing])
       [services-editing-container route]
       [services-entry-container route])]))


(defn service-layout [route] ;;receives services, which is obtained from graphql
  [:div

   [create-service-modal]

   (typography/view-header "Premium Services" "Manage and view premium service offerings")

   [:div.mb2
    [menu route]]

   ;; render subviews based on the active menu item
   (case (:page route)
     :services/list          [services-subview route]
     :services/entry         [services-subview route]
     :services.orders/list   [orders-views/subview])])







;; =====================================================
;; route handlers
;; =====================================================

;; services list
(defmethod content/view :services [route]
  [service-layout route])

;; services entry
(defmethod content/view :services/entry [route]
  [service-layout route])
