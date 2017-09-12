(ns odin.orders.admin.views
  (:require [antizer.reagent :as ant]
            [cljs.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]
            [iface.chart :as chart]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


;;; What do we want to see here?
;; - Graph of order revenue for current time period by community
;;   + https://www.highcharts.com/demo/column-drilldown
;;   + top-level columns by community
;;   + drill-down by service
;; - Graph of new orders placed for current time period
;; - "open" orders: those orders that are waiting to be charged

;; =============================================================================
;; Charts
;; =============================================================================


;; =============================================================================
;; Revenue


(defn- service-revenue-chart-options [showing]
  [ant/button {:type     "dashed"
               :icon     (if @showing "up" "down")
               :on-click #(swap! showing not)}
   "Controls"])


(defn- submit-form-change! [errors values]
  ;; TODO: errors?
  (dispatch [:orders.admin.chart.revenue/params values]))


(defn- service-revenue-chart-controls []
  (let [params (subscribe [:orders.admin.chart.revenue/params])]
    (fn []
      (let [form (ant/get-form)]
        [:div.chart-controls
         [ant/form
          {:on-submit (fn [event]
                        (.preventDefault event)
                        (ant/validate-fields form submit-form-change!))}
          [:div.columns
           [:div.column
            [ant/form-item {:label "X Axis" :key :chart-type}
             (ant/decorate-field form "chart-type" {:initial-value (:chart-type @params)}
                                 [ant/select
                                  [ant/select-option {:value "community"} "Community"]
                                  [ant/select-option {:value "billing"} "Billing Type"]])]]
           [:div.column
            [ant/form-item {:label "Within Period" :key :date-range}
             (ant/decorate-field form "date-range" {:initial-value [(:from @params) (:to @params)]}
                                 [ant/date-picker-range-picker {:format "l"}])]]]
          [:div
           [ant/form-item
            [ant/button
             {:type     "ghost"
              :on-click #(dispatch [:orders.admin.chart.revenue/export-csv])}
             "Download CSV"]
            [ant/button {:type "primary" :html-type "submit"} "Update"]]]]]))))


(defn- service-revenue-chart [config]
  (let [is-loading       (subscribe [:loading? :orders.admin.chart.revenue/fetch])
        form             (ant/get-form)
        showing-controls (r/atom false)]
    (fn [config]
      [ant/card {:title   (r/as-element [:b "Premium Service Order Revenue"])
                 :extra   (r/as-element [service-revenue-chart-options showing-controls])
                 :loading @is-loading}
       (when @showing-controls
         (r/as-element (ant/create-form (service-revenue-chart-controls))))
       [chart/chart config]])))


;; =============================================================================
;; Entrypoint
;; =============================================================================


(defn view []
  (let [config (subscribe [:orders.admin.chart.revenue/config])]
    [:div
     [:div.view-header
      [:h1 "Orders"]]
     [:div.columns
      [:div.column.is-half
       [service-revenue-chart @config]]]]))
