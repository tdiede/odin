(ns odin.orders.admin.list.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [odin.components.order :as order]
            [odin.orders.admin.create :as create]
            [odin.orders.admin.list.db :as db]
            [odin.utils.formatters :as format]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]
            [odin.routes :as routes]))

;;; How might we use an orders view?
;; - Orders begin life as "pending" (i.e. "new") -- these require attention.
;;   - Orders that are pending that have a price already set can be processed
;; - "Placed" orders can be considered "in-progress" and non-cancelable
;;   - Orders that are placed can be charged.

;;; We'll want to be able to filter orders by status, sort by creation date


;; =============================================================================
;; Table
;; =============================================================================


(defn- wrap-cljs [f]
  (fn [x record]
    (r/as-element (f x (js->clj record :keywordize-keys true)))))


(defn- render-total [_ {:keys [price quantity]}]
  (format/currency (* price (or quantity 1))))


(defn- render-price [_ {price :price}]
  (if (some? price) (format/currency price) "N/A"))


(defn- render-date [date _]
  [ant/tooltip {:title (when-some [d date] (format/date-time-short d))}
   (if (some? date) (format/date-short-num date) "N/A")])


(defn- status-icon-class [status]
  (get
   {:placed    "has-text-info"
    :fulfilled "has-text-primary"
    :failed    "has-text-warning"
    :charged   "has-text-success"
    :canceld   "has-text-danger"}
   status))


(defn- render-status [_ {status :status}]
  [ant/tooltip {:title status}
   [ant/icon {:class (status-icon-class (keyword status))
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
    :render    (wrap-cljs render-status)}
   {:title     "Order"
    :dataIndex :name
    :filters   (set (map (fn [{{id :id code :code} :service}] {:text code :value id}) orders))
    :onFilter  (fn [value record]
                 (= value (str (goog.object/getValueByKeys record "service" "id"))))
    :render    #(r/as-element
                 [:a {:href                    (routes/path-for :orders/entry :order-id (.-id %2))
                      :dangerouslySetInnerHTML {:__html %1}}])}
   {:title     "Member"
    :dataIndex :account
    :filters   (set (map (fn [{{id :id name :name} :account}] {:text name :value id}) orders))
    :onFilter  (fn [value record]
                 (= value (str (goog.object/getValueByKeys record "account" "id"))))
    :render    (wrap-cljs render-member-name)}
   {:title     (r/as-element [sort-col query-params :created "Created" db/params->route])
    :dataIndex :created
    :render    (wrap-cljs render-date)}
   {:title     (r/as-element [sort-col query-params :billed_on "Billed On" db/params->route])
    :dataIndex :billed_on
    :render    (wrap-cljs render-date)}
   {:title     (r/as-element [sort-col query-params :price "Price" db/params->route])
    :dataIndex :price
    :render    (wrap-cljs render-price)}
   {:title     "#"
    :dataIndex :quantity
    :render    (fnil format/number 1)}
   {:title     "Total"
    :dataIndex :total
    :render    (wrap-cljs render-total)}])


(defn- expanded [record]
  (let [record (js->clj record :keywordize-keys true)]
    [:div.columns
     [:div.column
      [:p.fs1 [:b "Billed"]]
      [:p.fs2 (get-in record [:service :billed])]]
     [:div.column
      [:p.fs1 [:b "Cost"]]
      [:p.fs2 (if-some [c (:cost record)] (format/currency c) "N/A")]]
     [:div.column.is-8
      [:p.fs1 [:b "Description/Notes"]]
      [:p.fs2 (get record "desc" "N/A")]]]))


(defn orders-table []
  (let [orders     (subscribe [:admin.table/orders])
        params     (subscribe [:admin.orders/query-params])
        is-loading (subscribe [:loading? :orders/query])]
    (fn []
      [ant/spin (tb/assoc-when
                 {:tip      "Fetch orders..."
                  :spinning @is-loading}
                 :delay (when-not (empty? @orders) 1000))
       [ant/table
        {:columns           (columns @params @orders)
         :expandedRowRender (comp r/as-element expanded)
         :dataSource        (map-indexed #(assoc %2 :key %1) @orders)}]])))


;; =============================================================================
;; Controls
;; =============================================================================


(defn- status-filters []
  (let [statuses (subscribe [:admin.orders/statuses])
        selected (subscribe [:admin.orders.statuses/selected])]
    [:div
     (doall
      (for [status @statuses]
        ^{:key status}
        [ant/tag-checkable-tag
         {:on-change #(dispatch [:admin.orders.status/select status])
          :checked   (@selected status)
          :style     {:font-size   16
                      :line-height "24px"
                      :height      "28px"}}
         [:span (name status)]]))]))


(defn- filter-by-members []
  (let [is-loading        (subscribe [:loading? :admin.orders/search-members])
        selected-accounts (subscribe [:admin.orders.accounts/selected])
        accounts          (subscribe [:admin.orders/members])]
    [ant/select
     {:placeholder       "select members"
      :style             {:width "100%"}
      :filter-option     false
      :not-found-content (when @is-loading (r/as-element [ant/spin {:size "small"}]))
      :mode              :multiple
      :label-in-value    true
      :value             (if-some [xs @selected-accounts] xs [])
      :allow-clear       true
      :on-search         #(dispatch [:admin.orders/search-members %])
      :on-change         #(dispatch [:admin.orders/select-members (js->clj % :keywordize-keys true)])}
     (doall
      (for [{:keys [id name]} @accounts]
        [ant/select-option {:key id} name]))]))


(defn- controls []
  (let [params        (subscribe [:admin.orders/query-params])
        filters-dirty (subscribe [:admin.orders.filters/dirty?])]
    [:div.chart-controls
     [:div.columns
      [:div.column.is-3
       [ant/form-item {:label "Filter by Members"}
        [filter-by-members]]]
      [:div.column.is-3
       [ant/form-item {:label "Within Range"}
        [ant/date-picker-range-picker
         {:format      "l"
          :allow-clear true
          :on-change   #(dispatch [:admin.orders.range/change (first %) (second %)])
          :value       ((juxt :from :to) @params)}]]]
      [:div.column.is-2
       [ant/form-item {:label "Calculate Range With"}
        [ant/select {:value     (:datekey @params)
                     :style     {:width 138}
                     :on-change #(dispatch [:admin.orders/datekey (keyword %)])}
         [ant/select-option {:value :created} "Created"]
         [ant/select-option {:value :billed} "Billed On"]]]]]
     [:div
      [ant/button {:on-click #(dispatch [:admin.orders.filters/reset])
                   :type     (when @filters-dirty :primary)
                   :icon     "filter"}
       "Reset Filters"]]]))


;; =============================================================================
;; Entrypoint
;; =============================================================================


(defn view []
  (let [query-params (subscribe [:admin.orders/query-params])]
    (fn []
      [:div
       (typography/view-header "Orders" "Manage and view premium service orders.")
       [:div.columns
        [:div.column
         [status-filters]]
        [:div.column
         [:div.is-pulled-right
          [create/button {:on-create [:orders/query @query-params]}]]]]

       [controls]

       [:div
        [orders-table]]])))
