(ns odin.metrics.views
  (:require [antizer.reagent :as ant]
            [odin.content :as content]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [cljsjs.highcharts]
            [cljsjs.highcharts-css]))


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


(defn chart [config]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (js/Highcharts.Chart. (r/dom-node this) (clj->js config)))
    :reagent-render
    (fn [config]
      [:div])}))


(defn referrals-options []
  [ant/dropdown
   {:overlay
    (r/as-element
     [ant/menu
      [ant/menu-item {:key 0}
       "Someting"]])}
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
       [chart (referrals-config @referrals)]]]]))


(defn financial []
  )


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
     [:div.view-header
      [:h1 "Metrics"]]
     [ant/tabs {:active-key @active
                :on-change  #(dispatch [:metrics.category/nav (keyword %)])}
      (for [{:keys [label key] :as t} tabs]
        [ant/tabs-tab-pane
         {:tab label :key key}
         [:div.container {:style {:padding "1px"}}
          [tab t]]])]]))


(defmethod content/view :metrics [route]
  [metrics-view])
