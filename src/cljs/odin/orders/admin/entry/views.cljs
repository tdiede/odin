(ns odin.orders.admin.entry.views
  (:require [antizer.reagent :as ant]
            [iface.loading :as loading]
            [iface.typography :as typography]
            [odin.orders.admin.entry.views.progress :as progress]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]
            [reagent.core :as r]
            [clojure.string :as string]
            [odin.components.order :as order]))


(defn- order-name
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
                 (when (some? sprice)
                   [:p.fs2 [:b "Service: "] (format/format "Service price is $%.2f" sprice)])
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
        :dangerouslySetInnerHTML {:__html (format/newlines->line-breaks text)}}]]]))


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


(defn- order-details
  [{:keys [service status name line_items billed_on fulfilled_on projected_fulfillment] :as order}]
  [:div
   [:h4.svc-title.mb1
    {:style {:font-weight 600 :margin-bottom 0}}
    (order-name order)
    [:div.pull-right
     [ant/button {:on-click #(dispatch [:admin.order/editing (:id order) true])} "Edit"]]]

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
       (line-items-table line_items)])]])


(defn- order-edit
  [order]
  (let [is-loading (subscribe [:loading? :admin.order/update!])
        form       (r/atom (-> order (update :line_items vec)))]
    (fn [order]
      [:div
       [order/form
        (:service order)
        @form
        {:on-change (fn [k v] (swap! form assoc k v))}]
       [:div.mt2.has-text-right
        [ant/button
         {:on-click #(dispatch [:admin.order/editing (:id order) false])}
         "Cancel"]
        [ant/button
         {:on-click #(reset! form order)}
         "Reset"]
        [ant/button
         {:type     :primary
          :disabled (or (= @form order)
                        (not (order/line-items-valid? (:line_items @form))))
          :loading  @is-loading
          :on-click #(dispatch [:admin.order/update! order @form])}
         "Save"]]])))


(defn order-card [order]
  (let [is-loading (subscribe [:loading? :order/fetch])
        is-editing (subscribe [:admin.order/editing? (:id order)])]
    [ant/card {:class     "svc"
               :bodyStyle {:padding "10px 16px"}
               :loading   @is-loading}
     (if @is-editing
       [order-edit order]
       [order-details order])]))


(defn- subheader [order]
  (let [account  (get-in order [:account :name])
        property (get-in order [:property :name])]
    [:span "for " [:a account] " at " [:a property]]))


(defn view [{{order-id :order-id} :params}]
  (let [order      (subscribe [:order (tb/str->int order-id)])
        is-loading (subscribe [:loading? :order/fetch])]
    (if (and @is-loading (nil? @order))
      (loading/fullpage :text "Fetching order...")
      [:div
       [:div.columns
        [:div.column.is-three-quarters
         (typography/view-header (:name @order) (subheader @order))]
        [:div.column.is-hidden-mobile.has-text-right
         [ant/button {:shape    :circle
                      :icon     "reload"
                      :loading  @is-loading
                      :on-click #(dispatch [:order/refresh order-id])}]]]

       [:div.columns
        [:div.column
         [progress/progress @order]]
        [:div.column
         [order-card @order]]]])))
