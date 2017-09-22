(ns odin.metrics.views
  (:require [antizer.reagent :as ant]
            [iface.chart :as chart]
            [iface.typography :as typography]
            [odin.content :as content]
            [odin.metrics.service-revenue :as service-revenue]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]))

;; What kinds of important things do we want to show here?


;; Use tabs for different sections?


;;; ACTIONS
;; - approve a member
;; - create a note for a member (or arbitrary entity)

;;; MARKETING
;; - referral sources w/in certain time period
;; - requested cities w/in certain time period
;; - number of new applications w/in certain time period
;; - number of new accounts created w/in certain time period


;;; FINANCIAL
;; - rent in pending/paid/due/overdue for current month
;; - service revenue per building (line chart) over time period
;; - individuals w/ overdue rent and/or security deposits


(def chart-theme
  {:chart
   {:style {:fontFamily "'Work Sans', 'Fira Sans', sans-serif"}}})


(set! js/Highcharts.theme (clj->js chart-theme))


(.setOptions js/Highcharts js/Highcharts.theme)


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
  (let [loading   (subscribe [:loading? :metrics.category/fetch])
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
