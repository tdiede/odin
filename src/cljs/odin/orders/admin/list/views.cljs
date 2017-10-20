(ns odin.orders.admin.list.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
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
    (f x (js->clj record :keywordize-keys true))))


(defn- render-total [_ {:keys [price quantity]}]
  (format/currency (* price (or quantity 1))))


(defn- render-price [_ {price :price}]
  (if (some? price) (format/currency price) "N/A"))


(defn- render-status [_ {status :status}]
  (case status
    :order.status/pending "new"
    :order.status/placed  "in-progress"
    status))


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
  [{:title     "Order"
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
    :render    #(.-name %)}
   {:title     (r/as-element [sort-col query-params :created "Created" db/params->route])
    :dataIndex :created
    :render    format/date-time-short}
   {:title     (r/as-element [sort-col query-params :billed_on "Billed On" db/params->route])
    :dataIndex :billed_on
    :render    #(if (some? %) (format/date-time-short %) "N/A")}
   {:title     (r/as-element [sort-col query-params :price "Price" db/params->route])
    :dataIndex :price
    :render    (wrap-cljs render-price)}
   {:title     "#"
    :dataIndex :quantity
    :render    (fnil format/number 1)}
   {:title     "Total"
    :dataIndex :total
    :render    (wrap-cljs render-total)}
   {:title     "Status"
    :dataIndex :status
    :render    (wrap-cljs render-status)}])


(defn- expanded [record]
  [:div.columns
   [:div.column
    [:p.fs1 [:b "Billed"]]
    [:p.fs2 (.. record -service -billed)]]
   [:div.column.is-10
    [:p.fs1 [:b "Description/Notes"]]
    [:p.fs2 (or (.-desc record) "N/A")]]])


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


(defn status-filters []
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



(defn- controls []
  (let [params (subscribe [:admin.orders/query-params])]
    [:div.chart-controls
     [:div.columns
      [:div.column.is-2
       [ant/form-item {:label "Calculate Range With"}
        [ant/select {:value     (:datekey @params)
                     :style     {:width 138}
                     :size      :large
                     :on-change #(dispatch [:admin.orders/datekey (keyword %)])}
         [ant/select-option {:value :created} "Created"]
         [ant/select-option {:value :billed} "Billed On"]]]]
      [:div.column
       [ant/form-item {:label "Within Range"}
        [ant/date-picker-range-picker
         {:format      "l"
          :allow-clear false
          :on-change   #(dispatch [:admin.orders.range/change (first %) (second %)])
          :value       ((juxt :from :to) @params)}]]]]]))


;; =============================================================================
;; Entrypoint
;; =============================================================================


(defn view []
  (let [showing-controls (r/atom false)
        query-params     (subscribe [:admin.orders/query-params])]
    (fn []
      [:div
       (typography/view-header "Orders" "Manage and view premium service orders.")
       [:div.columns
        (when-not @showing-controls
          {:style {:margin-top 24 :margin-bottom 24}})
        [:div.column
         [status-filters]]
        [:div.column
         [:div.is-pulled-right
          [create/button {:on-create [:orders/query @query-params]}]
          [ant/button
           {:class    "ml2"
            :type     (if @showing-controls :primary :default)
            :shape    :circle
            :icon     :setting
            :on-click #(swap! showing-controls not)}]]]]

       (when @showing-controls [controls])


       [:div
        [orders-table]]])))
