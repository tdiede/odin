(ns admin.services.orders.views
  (:require [admin.content :as content]
            [admin.services.orders.views.progress :as progress]
            [admin.services.orders.create :as create]
            [admin.services.orders.db :as db]
            [admin.routes :as routes]
            [antizer.reagent :as ant]
            [clojure.string :as string]
            [iface.components.order :as order]
            [iface.components.table :as table]
            [iface.loading :as loading]
            [iface.components.typography :as typography]
            [iface.components.payments :as payments]
            [iface.utils.formatters :as format]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]))



;; ==============================================================================
;; entry ========================================================================
;; ==============================================================================


(defn order-name
  [{:keys [name rental]}]
  [:span
   {:dangerouslySetInnerHTML
    {:__html (str name (if rental "<i> (rental)</i>" ""))}}])


(defn- order-price
  [{:keys [price quantity service variant]}]
  (let [quantity     (or quantity 1)
        sprice       (when-let [p (or (:price variant) (:price service))]
                       (and (not= price p) p))
        show-tooltip (or (> quantity 1) sprice)]
    [ant/tooltip
     {:title (when show-tooltip
               (r/as-element
                [:div
                 (when sprice
                   [:p.fs2 [:b "Service: "]
                    (format/format "Service price is $%.2f" sprice)])
                 (when (> quantity 1)
                   [:p.fs2
                    [:b "Calculation: "]
                    [:span {:dangerouslySetInnerHTML
                            {:__html (format/format "($%.2f&times;%d)" price quantity)}}]])]))}
     [:span
      {:dangerouslySetInnerHTML
       {:__html
        (str (format/format "$%.2f" (* price quantity))
             (when (= (:billed service) :monthly) "/month")
             (when show-tooltip "<b>*</b>"))}}]]))


(defn- order-cost
  [{:keys [cost service quantity]}]
  (let [quantity (or quantity 1)
        scost    (when-let [c (:cost service)] (and (not= c cost) c))]
    (if (nil? cost)
      [ant/tooltip {:title "Please input a cost."}
       "N/A"]
      [ant/tooltip
       {:title (when (some? scost)
                 (format/format "Service cost is $%.2f" scost))}
       [:span
        {:dangerouslySetInnerHTML
        {:__html
          (str (format/format "$%.2f" (* cost quantity))
               (when (some? scost) "<b>*</b>")
               (when (> quantity 1)
                 (format/format "<small> ($%.2f&times;%d)</small>" cost quantity)))}}]])))


(defn- order-margin
  [{:keys [cost price]}]
  (if (nil? cost)
    [ant/tooltip {:title "Please input a cost."} "N/A"]
    (str (format/format "%.2f" (* (- 1 (/ cost price)) 100)) "%")))


(defn- order-text-field [label text]
  (when (and (some? text) (not (string/blank? text)))
    [:div.columns
     [:div.column
      [:p.heading label]
      [:p.fs2
       {:style {:word-break "break-word"}
        :dangerouslySetInnerHTML {:__html (when-some [s text] (format/newlines->line-breaks s))}}]]]))


(def ^:private line-item-columns
  [{:key       "desc"
    :dataIndex "desc"
    :title     "Description"
    :width     "70%"}
   {:key       "cost"
    :dataIndex "cost"
    :title     "Cost"
    :render    (fn [cost] (if-let [c cost] (format/currency c) "N/A"))}
   {:key       "price"
    :dataIndex "price"
    :title     "Price"
    :render    (fn [price] (if-let [p price] (format/currency p) "N/A"))
    :className "has-text-right"}])


(defn- line-items-table [line-items]
  [ant/table
   {:dataSource (clj->js line-items)
    :columns    line-item-columns
    :size       :small
    :class      "is-flush"
    :pagination false}])


(defn- order-fields-list-entry
  [{:keys [label value type]}]
  [:div.mb2
   [:div
    [:p [:b label] " " (case type
                         :date (format/date-time-short value)
                         :time (format/time-short value)
                         (str value))]]])


(defn- order-fields-list
  [fields]
  [:div
   [:p.heading "Order Form Details"]
   (map (fn [field]
          (with-meta
            [order-fields-list-entry field]
            {:key (:id field)}))
        fields)])


(defn order-details
  ([order]
   (order-details order {}))
  ([{:keys [service status name fields line_items billed_on fulfilled_on projected_fulfillment] :as order}
    {:keys [on-click] :or {on-click identity}}]
   [:div
    [:h4.svc-title.mb1
     {:style {:font-weight 600 :margin-bottom 0}}
     (order-name order)
     [:div.pull-right
      [ant/button {:on-click on-click} "Edit"]]]

    [:p.svc-desc.fs2.mb1
     {:dangerouslySetInnerHTML {:__html (:desc service)}}]

    [:div.svc-foot
     [:div.columns.mb0
      [:div.column
       [:p.heading "Price"]
       [:p (order-price order)]]
      [:div.column
       [:p.heading "Cost"]
       [:p (order-cost order)]]
      [:div.column.is-one-quarter
       [:p.heading "Margin"]
       [:p (order-margin order)]]]

     [order-fields-list fields]

     (when (or (some? fulfilled_on) (some? projected_fulfillment))
       [:div.columns
        (when-some [p projected_fulfillment]
          [:div.column
           [:p.heading "Projected Fulfillment"]
           [:p (format/date-time-short p)]])
        (when-some [f fulfilled_on]
          [:div.column
           [:p.heading "Fulfilled"]
           [:p (format/date-time-short f)]])
        (when-some [b billed_on]
          [:div.column
           [:p.heading "Billed On"]
           [:p (format/date-time-short b)]])])
     (order-text-field "Request Notes" (:request order))
     (order-text-field "Fulfillment Notes" (:summary order))
     (when-not (empty? line_items)
       [:div
        [:p.heading "Line Items"]
        (line-items-table line_items)])]]))


(defn- order-edit
  [order]
  (let [is-loading (subscribe [:ui/loading? :services.order/update!])
        form       (r/atom (-> order (update :line_items vec)))]
    (fn [order]
      [:div
       [order/form
        (:service order)
        @form
        {:on-change (fn [k v] (swap! form assoc k v))}]
       [:div.mt2.has-text-right
        [ant/button
         {:on-click #(dispatch [:services.order/editing (:id order) false])}
         "Cancel"]
        [ant/button
         {:on-click #(reset! form order)}
         "Reset"]
        [ant/button
         {:type     :primary
          :disabled (or (= @form order)
                        (not (order/line-items-valid? (:line_items @form))))
          :loading  @is-loading
          :on-click #(dispatch [:services.order/update! order @form])}
         "Save"]]])))


(defn order-card [order]
  (let [is-loading (subscribe [:ui/loading? :services.order/fetch])
        is-editing (subscribe [:services.order/editing? (:id order)])]
    [ant/card {:class     "svc"
               :bodyStyle {:padding "10px 16px"}
               :loading   @is-loading}
     (if @is-editing
       [order-edit order]
       [order-details order {:on-click #(dispatch [:services.order/editing (:id order) true])}])]))


(defn- subheader [order]
  (let [account  (:account order)
        property (get-in order [:property :name])]
    [:span "for "
     [:a {:href (routes/path-for :accounts/entry :account-id (:id account))}
      (:name account)]
     " at " [:a property]]))


(defmethod content/view :services.orders/entry
  [{{order-id :order-id} :params}]
  (let [order      (subscribe [:order (tb/str->int order-id)])
        is-loading (subscribe [:ui/loading? :services.order/fetch])]
    (if (or @is-loading (nil? @order))
      (loading/fullpage :text "Fetching order...")
      [:div
       [:div.columns
        [:div.column.is-three-quarters
         (typography/view-header (:name @order) (subheader @order))]
        [:div.column.is-hidden-mobile.has-text-right
         [ant/button {:shape    :circle
                      :icon     "reload"
                      :loading  @is-loading
                      :on-click #(dispatch [:services.order/refresh order-id])}]]]

       [:div.columns
        [:div.column
         [progress/progress @order]]
        [:div.column
         [order-card @order]

         [ant/card {:class "is-flush"}
          [payments/payments-table (:payments @order) @is-loading]]]]])))


;; ==============================================================================
;; list =========================================================================
;; ==============================================================================


;; table ========================================================================


(defn- render-total [_ {:keys [price quantity]}]
  (format/currency (* price (or quantity 1))))


(defn- render-price [_ {price :price}]
  (if (some? price) (format/currency price) "N/A"))


(defn- render-date [date _]
  [ant/tooltip {:title (when-some [d date] (format/date-time-short d))}
   (if (some? date) (format/date-short-num date) "N/A")])


(defn- render-status [_ {status :status}]
  [ant/tooltip {:title status}
   [ant/icon {:class (order/status-icon-class (keyword status))
              :type  (order/status-icon (keyword status))}]])


(defn- render-member-name [_ {account :account}]
  [ant/tooltip {:title (format/format "%s @ %s" (:email account) (get-in account [:property :name]))}
   (:name account)])


(defn- sort-col [query-params key title href-fn]
  (let [{:keys [sort-by sort-order]} query-params]
    [:span title
     [:div.ant-table-column-sorter
      [:a.ant-table-column-sorter-up
       {:class (if (and (= sort-by key) (= sort-order :asc)) "on" "off")
        :href  (href-fn (assoc query-params :sort-order :asc :sort-by key))}
       [ant/icon {:type "caret-up"}]]
      [:a.ant-table-column-sorter-down
       {:class (if (and (= sort-by key) (= sort-order :desc)) "on" "off")
        :href  (href-fn (assoc query-params :sort-order :desc :sort-by key))}
       [ant/icon {:type "caret-down"}]]]]))


(defn- columns [query-params orders]
  [{:title     ""
    :dataIndex :status
    :render    (table/wrap-cljs render-status)}
   {:title     "Order"
    :dataIndex :name
    :filters   (set (map (fn [{{id :id code :code} :service}] {:text code :value id}) orders))
    :onFilter  (fn [value record]
                 (= value (str (goog.object/getValueByKeys record "service" "id"))))
    :render    #(r/as-element
                 [:a {:href                    (routes/path-for :services.orders/entry :order-id (.-id %2))
                      :dangerouslySetInnerHTML {:__html %1}}])}
   {:title     "Member"
    :dataIndex :account
    :filters   (set (map (fn [{{id :id name :name} :account}] {:text name :value id}) orders))
    :onFilter  (fn [value record]
                 (= value (str (goog.object/getValueByKeys record "account" "id"))))
    :render    (table/wrap-cljs render-member-name)}
   {:title     (table/sort-col-title query-params :created "Created" db/params->route)
    :dataIndex :created
    :render    (table/wrap-cljs render-date)}
   {:title     (table/sort-col-title query-params :billed_on "Billed On" db/params->route)
    :dataIndex :billed_on
    :render    (table/wrap-cljs render-date)}
   {:title     (table/sort-col-title query-params :price "Price" db/params->route)
    :dataIndex :price
    :render    (table/wrap-cljs render-price)}
   {:title     "#"
    :dataIndex :quantity
    :render    (fnil format/number 1)}
   {:title     "Total"
    :dataIndex :total
    :className "has-text-right"
    :render    (table/wrap-cljs render-total)}])


(defn- expanded [record]
  (let [record (js->clj record :keywordize-keys true)]
    [:div.columns
     [:div.column
      [:p.fs1 [:b "Billed"]]
      [:p.fs2 (get-in record [:service :billed])]]
     [:div.column
      [:p.fs1 [:b "Cost"]]
      [:p.fs2 (if-some [c (:cost record)] (format/currency c) "N/A")]]
     [:div.column.is-4
      [:p.fs1 [:b "Request Notes"]]
      [:p.fs2 (get record :request "N/A")]]
     [:div.column.is-4
      [:p.fs1 [:b "Fulfillment Notes"]]
      [:p.fs2 (get record :summary "N/A")]]]))


(defn orders-table []
  (let [orders     (subscribe [:services.orders/table])
        params     (subscribe [:services.orders/query-params])
        is-loading (subscribe [:ui/loading? :services.orders/query])]
    (fn []
      [ant/spin (tb/assoc-when
                 {:tip      "Fetch orders..."
                  :spinning @is-loading}
                 :delay (when-not (empty? @orders) 1000))
       [ant/table
        {:columns           (columns @params @orders)
         :expandedRowRender (comp r/as-element expanded)
         :dataSource        (map-indexed #(assoc %2 :key %1) @orders)}]])))


;; controls =====================================================================


(defn- status-filters []
  (let [statuses (subscribe [:services.orders/statuses])
        selected (subscribe [:orders.statuses/selected])]
    [:div
     (doall
      (for [status @statuses]
        ^{:key status}
        [ant/tag-checkable-tag
         {:on-change #(dispatch [:orders.status/select status])
          :checked   (@selected status)
          :style     {:font-size   16
                      :line-height "24px"
                      :height      "28px"}}
         [:span (name status)]]))]))


(defn- filter-by-members []
  (let [is-loading        (subscribe [:ui/loading? :services.orders/search-members])
        selected-accounts (subscribe [:orders.accounts/selected])
        accounts          (subscribe [:services.orders/members])]
    [ant/select
     {:placeholder       "select members"
      :style             {:width "100%"}
      :filter-option     false
      :not-found-content (when @is-loading (r/as-element [ant/spin {:size "small"}]))
      :mode              :multiple
      :label-in-value    true
      :value             (if-some [xs @selected-accounts] xs [])
      :allow-clear       true
      :on-search         #(dispatch [:services.orders/search-members %])
      :on-change         #(dispatch [:services.orders/select-members (js->clj % :keywordize-keys true)])}
     (doall
      (for [{:keys [id name]} @accounts]
        [ant/select-option {:key id} name]))]))


(defn controls []
  (let [params        (subscribe [:services.orders/query-params])
        filters-dirty (subscribe [:orders.filters/dirty?])]
    [:div.table-controls
     [:div.columns
      [:div.column.is-3
       [ant/form-item {:label "Filter by Members"}
        [filter-by-members]]]
      [:div.column.is-3
       [ant/form-item {:label "Within Range"}
        [ant/date-picker-range-picker
         {:format      "l"
          :allow-clear true
          :on-change   #(dispatch [:orders.range/change (first %) (second %)])
          :value       ((juxt :from :to) @params)}]]]
      [:div.column.is-2
       [ant/form-item {:label "Calculate Range With"}
        [ant/select {:value     (:datekey @params)
                     :style     {:width 138}
                     :on-change #(dispatch [:services.orders/datekey (keyword %)])}
         [ant/select-option {:value :created} "Created"]
         [ant/select-option {:value :billed} "Billed On"]]]]]
     [:div
      [ant/button {:on-click #(dispatch [:orders.filters/reset])
                   :type     (when @filters-dirty :primary)
                   :icon     "filter"}
       "Reset Filters"]]]))


;; entrypoint ===================================================================
;; rendered by `service-layout` component

(defn subview []
  (let [query-params (subscribe [:services.orders/query-params])]
    [:div
     [:div.columns
      [:div.column
       [status-filters]]
      [:div.column
       [:div.is-pulled-right
        [create/button {:on-create [:services.orders/query @query-params]}]]]]

     [controls]

     [:div
      [orders-table]]]))
