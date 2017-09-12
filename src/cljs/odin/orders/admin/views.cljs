(ns odin.orders.admin.views
  (:require [antizer.reagent :as ant]
            [cljs.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]
            [iface.chart :as chart]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


;;; What do we want to see here?
;; - Graph of order revenue for current month by community
;;   + https://www.highcharts.com/demo/column-drilldown
;;   + top-level columns by community
;;   + drill-down by service
;; - "open" orders: those orders that are waiting to be charged

;; =============================================================================
;; Chart
;; =============================================================================


;; =============================================================================
;; Views
;; =============================================================================


(defn- orders-chart-options [showing]
  [ant/button {:type     "dashed"
               :icon     (if @showing "up" "down")
               :on-click #(swap! showing not)}
   "Controls"])


(defn- submit-form-change! [errors values]
  ;; TODO: errors?
  (dispatch [:orders.admin.chart/params values]))


(defn- orders-chart-controls []
  (let [params (subscribe [:orders.admin.chart/params])]
    (fn []
      (let [form (ant/get-form)]
        [:div {:style {:padding       24
                       :background    "#fbfbfb"
                       :border        "1px solid #d9d9d9"
                       :border-radius 6
                       :margin-bottom 24}}
         [ant/form {:on-submit (fn [event]
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
            [ant/button {:type "ghost" :style {:margin-right 6}} "Download CSV"]
            [ant/button {:type "primary" :html-type "submit"} "Update"]]]]]))))


(defn- orders-chart [config]
  (let [form             (ant/get-form)
        showing-controls (r/atom false)]
    (fn [config]
      [ant/card {:title (r/as-element [:b "Premium Service Order Revenue"])
                 :extra (r/as-element [orders-chart-options showing-controls])}
       (when @showing-controls
         (r/as-element (ant/create-form (orders-chart-controls))))
       [chart/chart config]])))


(defn view []
  (let [config (subscribe [:orders.admin.chart/config])]
    [:div
     [:div.view-header
      [:h1 "Orders"]]
     [:div.columns
      [:div.column.is-half
       [orders-chart @config]]]]))
