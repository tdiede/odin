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
  [field {:keys [index value]}]
  [:div.columns
   [:div.column.is-8
    [ant/input
     {:placeholder "label"
      :value       value
      :on-change   #(dispatch [:service.form.field.option/update (:index field) index (.. % -target -value)])}]]
   [:div.column.is-1
    [ant/button
     {:icon     "close-circle-o"
      :shape    "circle"
      :type     "danger"
      :on-click #(dispatch [:service.form.field.option/delete (:index field) index])}]]
   [:div.column.is-3
    [ant/button-group
     [ant/button
      {:icon     "up"
       :type     "primary"
       :disabled (zero? index)
       :on-click #(dispatch [:service.form.field.option/reorder field index (dec index)])}]
     [ant/button
      {:icon     "down"
       :type     "primary"
       :disabled @(subscribe [:services.form.field.option/is-last? field index])
       :on-click #(dispatch [:service.form.field.option/reorder field index (inc index)])}]]]])

(defn render-service-field-options-entry
  [index option]
  (with-meta
    [service-field-options-entry index option]
    {:key (:index option)}))

(defn service-field-options-popover
  [{:keys [index] :as field} options]
  [:div
   (doall (map (partial render-service-field-options-entry field) options))
   [:div.columns
    [:div.column.is-6.is-offset-3
     [ant/button
      {:style    {:width "100%"}
       :on-click #(dispatch [:service.form.field.option/create index])}
      "Add Option"]]]])


(defn service-field-options-container
  [field options]
  [ant/form-item
   {:label (when (zero? (:index field)) "Options")}
   [ant/popover
    {:title         "Menu Options"
     :overlay-style {:width "30%"}
     :content       (r/as-element [service-field-options-popover field options])}
    [ant/button
     {:style {:width "100%"}}
     (str "Menu Options (" (count options) ")")]]])


(defn- service-field-date-button
  [field-index day-of-week day]
  [ant/button
   {:type     (if @(subscribe [:service.form.field.date/is-excluded? field-index day-of-week])
                :default
                :primary)
    :on-click #(dispatch [:service.form.field.date/toggle-excluded field-index day-of-week])}
   day])


(defn- service-field-settings-date
  [{:keys [index] :as field}]
  (let [days ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]]
    [:div.has-text-centered
     [ant/form-item
      {:label "Available Days"}
      [ant/button-group
       {:size "small"}
       (map-indexed
        (fn [i day]
          (with-meta
            [service-field-date-button index i day]
            {:key day}))
        days)]]]))


(defn- service-field-settings-date-container
  [field]
  [ant/form-item
   {:label (when (zero? (:index field)) "Settings")}
   [ant/popover
    {:title "Settings"
     :overlay-style {:width "30%"}
     :content (r/as-element [service-field-settings-date field])}
    [ant/button
     {:style {:width "100%"}}
     "Settings"]]])


(defmulti render-service-field :type)


(defmethod render-service-field :dropdown
  [{:keys [index label required type options] :as field}]
  [:div.columns
   [:div.column.is-1
    [service-field-type index type]]
   [:div.column.is-5
    [service-field-label index label]]
   [:div.column.is-3
    [service-field-options-container field options]]
   [:div.column.is-1
    [service-field-required index required]]
   [:div.column.is-1
    [service-field-remove index]]
   [:div.column.is-2
    [service-field-order index]]])


(defmethod render-service-field :date
  [{:keys [index label required type options] :as field}]
  [:div.columns
   [:div.column.is-1
    [service-field-type index type]]
   [:div.column.is-5
    [service-field-label index label]]
   [:div.column.is-3
    [service-field-settings-date-container field]]
   [:div.column.is-1
    [service-field-required index required]]
   [:div.column.is-1
    [service-field-remove index]]
   [:div.column.is-2
    [service-field-order index]]])


(defmethod render-service-field :default
  [{:keys [index label required type]}]
  [:div.columns
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
   (doall
    (map
     #(with-meta
        [render-service-field %]
        {:key (:index %)})
     fields))])


(defn fees-edit-item
  [{:keys [name price id]}]
  [:p [ant/button
       {:type     "danger"
        :shape    "circle"
        :icon     "close-circle-o"
        :on-click #(dispatch [:service.form.fee/remove id])}]
   (str " " name " (" (format/currency price) ")") ])


(defn add-fees-menu-item
  [{:keys [id name price]}]
  [ant/menu-item {:key id} (str name " (" (format/currency price) ")")])


(defn add-fees-menu
  [fees]
  (let [menu
        [ant/menu {:on-click #(dispatch [:service.form.fee/add (long (aget % "key"))])}
         (doall (map add-fees-menu-item fees))]]
    [ant/dropdown
     {:overlay (r/as-element menu)}
     [ant/button
      {:style {:width "80%"}}
      "Add Fee"
      [ant/icon {:type "down"}]]]))


(defn create-service-form []
  (let [form       (subscribe [:services/form])
        catalogs   (subscribe [:services/catalogs])
        is-editing (subscribe [:services/is-editing])]
    [:div
     [ant/card {:title "Service Details"}
      [:div.columns
       [:div.column.is-5
        [ant/form-item
         (merge
          {:label "Service Name"
           :type  "text"}
          (if @(subscribe [:service.form/is-valid? :name])
            {}
            {:help            "Please provide a name for this service."
             :has-feedback    true
             :validate-status "error"}))
         [ant/input
          {:placeholder "service name"
           :value       (:name @form)
           :on-change   #(dispatch [:service.form/update :name (.. % -target -value)])}]]
        [ant/form-item
         (merge
          {:label "Description"}
          (if @(subscribe [:service.form/is-valid? :description])
            {}
            {:help            "Please provide a description for this service."
             :has-feedback    true
             :validate-status "error"}))
         [ant/input-text-area
          {:rows        6
           :placeholder "description"
           :value       (:description @form)
           :on-change   #(dispatch [:service.form/update :description (.. % -target -value)])}]]]
       [:div.column.is-4
        [ant/form-item
         (merge
          {:label "Code"
           :type  "text"
           :extra "This code must be unique to only this service."}
          (if @(subscribe [:service.form/is-valid? :code])
            {}
            {:help            "Please provide a unique code for this service."
             :has-feedback    true
             :validate-status "error"}))
         [ant/input
          {:placeholder "service code"
           :value       (:code @form)
           :disabled    @is-editing
           :on-change   #(dispatch [:service.form/update :code (.. % -target -value)])}]]
        [ant/form-item
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
                           :value (clojure.core/name catalog)}
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
       [:div.column.is-2
        [:div
         [ant/form-item
          {:label "Active?"}
          [ant/switch
           {:checked   (:active @form)
            :on-change #(dispatch [:service.form/update :active %])}]]

         [ant/form-item
          {:label "Type"}
          [ant/select
           {:style {:width "100%"}
            :default-value (:type @form)
            :on-change #(dispatch [:service.form/update :type (keyword %)])}
           [ant/select-option
            {:value :service}
            "service"]
           [ant/select-option
            {:value :fee}
            "fee"]]]]]]]

     [ant/card {:title "Pricing/Billing"}
      [:div
       [:div.columns
        [:div.column.is-3
         [ant/form-item
          {:label "Price"}
          [ant/input-number
           {:value     (:price @form)
            :style     {:width "75%"}
            :formatter (fn [value] (str "$" value))
            :on-change #(dispatch [:service.form/update :price %])}]]
         [ant/form-item
          {:label "Billed"}
          [ant/select
           {:style         {:width "75%"}
            :placeholder   "billed"
            :default-value (:billed @form)
            :on-change     #(dispatch [:service.form/update :billed (keyword %)])}
           [ant/select-option {:value :once} "once"]
           [ant/select-option {:value :monthly} "monthly"]]]]
        [:div.column.is-3
         [ant/form-item
          {:label "Cost"}
          [ant/input-number
           {:value     (:cost @form)
            :style     {:width "75%"}
            :formatter (fn [value] (str "$" value))
            :on-change #(dispatch [:service.form/update :cost %])}]]
         [ant/form-item
          {:label "Rental?"}
          [ant/checkbox
           {:checked   (:rental @form)
            :on-change #(dispatch [:service.form/update :rental (.. % -target -checked)])}]]]

        [:div.column.is-6
         [:div
          [ant/form-item {:label "Fees"}]]
         [:div.mt2
          [add-fees-menu @(subscribe [:services/fees])]]
         [:div.mb2
          [:br]
          (if (empty? (:fees @form))
            "No fees yet!"
            (doall (map
                    (fn [fee-id]
                      (let [fee @(subscribe [:service fee-id])]
                        (with-meta
                          [fees-edit-item fee]
                          {:key (:id fee)})  ))
                    (:fees @form))))]]]]]

     [fields-card (:fields @form)]]))


(defn create-service-modal []
  (let [form (subscribe [:services/form])]
    [ant/modal
     {:title       "Create Service"
      :width       "70%"
      :visible     @(subscribe [:modal/visible? :service/create-service-form])
      :ok-text     "Save New Service"
      :on-cancel   #(dispatch [:service.form/hide])
      :on-ok       #(dispatch [:service.create/validate @form])
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


(defn- path->selected
  [path]
  (case (vec (rest path))
    [:list]            :services
    [:orders :list]    :orders
    [:archived :list]  :archived
    [:archived :entry] :archived
    :services))


(defn menu [route]
  [ant/menu {:mode                  :horizontal
             :selected-keys          [(path->selected (:path route))]}
   [ant/menu-item {:key :services}
    [:a {:href (routes/path-for :services/list)}
     "Service Offerings"]]
   [ant/menu-item {:key :orders}
    [:a {:href (routes/path-for :services.orders/list)}
     "Manage Orders"]]
   [ant/menu-item {:key :archived}
    [:a {:href (routes/path-for :services.archived/list)}
     "Archived Services"]]])


(defn- services-list [path services]
  (let [path        (if (some #(= :archived %) path)
                      :services.archived/entry
                      :services/entry)
        columns     [{:title     "Name"
                      :dataIndex "name"
                      :key       "name"
                      :render    #(r/as-element
                                   [:a {:href                    (routes/path-for path :service-id (aget %2 "id"))
                                        :dangerouslySetInnerHTML {:__html %1}}])
                      }]
        search-text @(subscribe [:services/search-text])
        is-loading  @(subscribe [:ui/loading? :services/query])]
    [ant/table
     {:columns     columns
      :show-header false
      :loading     is-loading
      :dataSource  (filter #(case-insensitive-includes? (:name %) search-text) services)}]))


;; =====================================================
;; service entry (detail view)
;; =====================================================


(def days-of-week ;; position in vector corresponds to momentjs' numerical indication of the day
  ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"])


(defn- service-entry-field-available-days
  [excluded]
  (let [included (clojure.set/difference #{0 1 2 3 4 5 6} excluded)]
    (reduce
     (fn [days day]
       (str days (get days-of-week day) " "))
     ""
     (sort included))))


(defn- service-entry-field [{:keys [id index type label required options] :as field}]
  [:div
   [:div.columns
    [:div.column.is-1
     (when (= 0 index)
       [:p [:b "Type"]])
     [:div (clojure.core/name type)]]

    [:div.column.is-9
     (when (= 0 index)
       [:p [:b "Label"]])
     [:p label]
     [:div
      (when (and (= :date type) true #_(not (empty? (:excluded_days field))))
        [:div
         [:b "Available Days: "]
         [:span (service-entry-field-available-days (:excluded_days field))]])
      (when (and (= :dropdown type) (not (empty? options)))
        [:div
         [:b "Options: "]
         (map
          (fn [option]
            (with-meta
              [:span
               (str
                (when (not= 0 (:index option))
                  ", ")
                (:label option))]
              {:key (:index option)}))
          options)])]]

    [:div.column.is-1
     (when (= 0 index)
       [:p [:b "Required?"]])
     [ant/switch {:checked required}]]]])


(defn- service-entry-fee
  [fee]
  [:div.columns
   [:div.column.is-1
    [:p (format/currency (:price fee))]]
   [:div.column.is-7
    [:p (:name fee)]]])


(defn archive-service-popover
  [service]
  [:div
   [:div
    [:b.fs2 "Are you sure you want to archive this service?"]
    [:p.fs2 "Doing this will remove this service from our current offerings"]]
   [:div.align-right
    {:style {:margin-top "10px"}}
    [ant/button
     {:on-click #(dispatch [:service/archive! service])}
     "Archive"]]])


(defn- service-entry [{:keys [path]} service]
  (let [{:keys [id name description code active price cost billed fees type
                rental catalogs properties order-count fields]} @service
        is-loading                                              @(subscribe [:ui/loading? :service/fetch])]
    [:div
     [:div.mb2
      (if (not (some #(= :archived %) path))
        [:div
         [ant/button
          {:on-click #(dispatch [:service/edit-service @service])}
          "Edit"]
         [ant/button
          {:on-click #(dispatch [:service/copy-service @service])}
          "Make a Copy"]
         [ant/popover
          {:content (r/as-element [archive-service-popover @service])}
          [ant/button "Archive"]]]
        [:div
         [ant/button
          {:on-click #(dispatch [:service/unarchive! @service])}
          "Unarchive"]])]
     [ant/card
      {:title   "Service Details"
       :loading is-loading}
      (when (and active (empty? properties))
        [:div.mb2
         [ant/alert
          {:type        :warning
           :show-icon   true
           :message     "This service is not currently visible to members"
           :description "While this service is active, it is not availalbe at any properties and members will not be able to see it. To make this service available to members, add some properties below."}]])
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
           [:p (doall (map-indexed
                       (fn [i property]
                         (let [property-name (:name  @(subscribe [:property property]))]
                           (if (zero? i)
                             property-name
                             (str ", " property-name))))
                       properties))])]]

       [:div.column.is-2
        [:p.mb1 [:b "Active?"]]
        [ant/switch {:checked active}]

        [:p.mt2 [:b "Type"]]
        [:p type]]]]

     [ant/card
      {:title   "Pricing/Billing"
       :loading is-loading}
      [:div.columns
       [:div.column.is-3
        [:div
         [:p [:b "Price"]]
         (if (nil? price)
           [:p "Quote"]
           [:p (str
                (format/currency price)
                (when (= :monthly billed)
                  "/month"))])]]
       [:div.column.is-3
        [:div
         [:p [:b "Cost"]]
         (if (nil? cost)
           [:p "n/a"]
           [:p (format/currency cost)])]]

       [:div.column.is-3
        [:div
         [:p [:b "Billed"]]
         [:p billed]]]

       [:div.column.is-3
        [:div
         [:p [:b "Rental?"]]
         [ant/checkbox {:checked rental}]]]]
      [:div
       [:p [:b "Fees" ]]
       (if (empty? fees)
         [:p "none"]
         (map
          #(with-meta
             [service-entry-fee %]
             {:key (:id %)})
          fees))]]


     [ant/card
      {:title   "Metrics"
       :loading is-loading}
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

     [ant/card
      {:title   "Order Form Fields"
       :loading is-loading}
      (if (empty? fields)
        [:p "none"]
        [:div
         (map
          #(with-meta
             [service-entry-field %]
             {:key (:id %)})
          fields)])]]))


(defn services-list-container [{:keys [page path]} services]
  [:div.column.is-3
   (.log js/console "page: " page " path: " path)
   (when (not (some #(= :archived %) path))
     [:div.mb2
      [ant/button
       {:style {:width "100%"}
        :type  :primary
        :icon  "plus"
        :on-click #(dispatch [:service.form/show])}
       "Create New Service/Fee"]])
   [:div.mb1
    [service-filter]]
   [services-list path @services]])


(defn services-entry-container [route]
  (if (not (nil? (get-in route [:params :service-id])))
    (when-let [service (subscribe [:service (tb/str->int (get-in route [:params :service-id]))])]
      [:div.column.is-9
       [service-entry route service]])
    [:p.mt2.ml2
     "Select a service from the list to see more details."]))


(defn services-editing-container [route]
  (let [service-id (subscribe [:service-id])
        form       (subscribe [:services/form])]
    [:div.column.is-9
     [:div.mb2
      [:div
       [ant/button
        {:on-click #(dispatch [:service/cancel-edit])}
        "Cancel"]
       [ant/button
        {:on-click #(dispatch [:service.edit/validate @service-id @form])}
        "Save Changes"]]]
     [create-service-form]]))


(defn services-subview
  [route services]
  [:div.columns
   [services-list-container route services]
   (if @(subscribe [:services/is-editing])
     [services-editing-container route]
     [services-entry-container route])])


(defn services-archived []
  [:div
   [:h1 "archived services"]])


(defn service-layout [route] ;;receives services, which is obtained from graphql
  [:div

   [create-service-modal]

   (typography/view-header "Helping Hands" "Manage and view Helping Hands service offerings")

   [:div.mb2
    [menu route]]

   ;; render subviews based on the active menu item
   (case (:page route)
     :services/list           [services-subview route (subscribe [:services/list])]
     :services/entry          [services-subview route (subscribe [:services/list])]
     :services.orders/list    [orders-views/subview]
     :services.archived/list  [services-subview route (subscribe [:services/archived])]
     :services.archived/entry [services-subview route (subscribe [:services/archived])])])







;; =====================================================
;; route handlers
;; =====================================================

;; services list
(defmethod content/view :services [route]
  [service-layout route])

;; services entry
(defmethod content/view :services/entry [route]
  [service-layout route])
