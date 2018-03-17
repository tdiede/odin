(ns admin.metrics.views
  (:require [admin.content :as content]
            [admin.metrics.service-revenue :as service-revenue]
            [antizer.reagent :as ant]
            [iface.chart :as chart]
            [iface.components.typography :as typography]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]))


(def chart-theme
  {:chart
   {:style {:fontFamily "'Work Sans', 'Fira Sans', sans-serif"}}})


(defn referrals-config [series-data]
  {:chart       {:plotBackgroundColor nil
                 :plotBorderWidth     nil
                 :plotShadow          false
                 :type                "pie"}
   :credits     {:enabled false}
   :title       {:text nil}
   :tooltip     {:pointFormat "{series.name}: <b>{point.percentage:.1f}%</b> ({point.count})"}
   :plotOptions {:pie {:allowPointSelect true
                       :cursor           "pointer"
                       :dataLabels       {:enabled true
                                          :format  "{point.name}: {point.y:.1f} %"}
                       :style            {:color (or (and js/Highcharts.theme
                                                          js/Highcharts.theme.contrastTextColor)
                                                     "black")}}}
   :series      [{:name         "referrals"
                  :colorByPoint true
                  :data         series-data}]})


(defn referrals-options []
  [ant/dropdown
   {:overlay
    (r/as-element
     [ant/menu
      [ant/menu-item {:key 0}
       "Something"]])}
   [:a.ant-dropdown-link "Options"
    [ant/icon {:type "down"}]]])


(defn marketing []
  (let [loading   (subscribe [:ui/loading? :metrics.category/fetch])
        referrals (subscribe [:metrics/referrals])]
    [:div.columns
     [:div.column.is-half
      [ant/card {:title   "Referrals"
                 :loading @loading
                 :extra   (r/as-element [referrals-options])}
       [chart/chart (referrals-config @referrals)]]]]))


(defn financial []
  [:div.columns
   [:div.column.is-half
    [service-revenue/chart]]])


(def tabs
  [{:label "Marketing"
    :key   :marketing}
   {:label "Financial"
    :key   :financial}])


(defmulti tab :key)
(defmethod tab :marketing [] [marketing])
(defmethod tab :financial [] [financial])


(defn- metrics-view []
  (let [active (subscribe [:metrics.category/current])]
    [:div
     (typography/view-header "Metrics" "Important metrics and stats about the company.")
     [ant/tabs {:active-key @active
                :on-change  #(dispatch [:metrics.category/nav (keyword %)])}
      (for [{:keys [label key] :as t} tabs]
        [ant/tabs-tab-pane
         {:tab label :key key}
         [:div.container {:style {:padding "1px"}}
          [tab t]]])]]))


(defmethod content/view :metrics [route]
  [metrics-view])
