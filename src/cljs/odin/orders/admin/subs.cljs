(ns odin.orders.admin.subs
  (:require [odin.orders.admin.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Helpers
;; =============================================================================


;; =============================================================================
;; Chart


(defn- sum-by-service [payments]
  (->> (group-by :service payments)
       (reduce (fn [acc [sname payments]]
                 (conj acc [sname (apply + (map :amount payments))]))
               [])))


(defn- revenue-drilldown
  [payments key]
  (->> (group-by key payments)
       (reduce (fn [acc [name payments]]
                 (conj acc {:name name
                            :id   name
                            :data (sum-by-service payments)}))
               [])))


(defn- revenue-series
  [payments key]
  (->> (group-by key payments)
       (reduce (fn [acc [name payments]]
                 (conj acc {:name      name
                            :y         (apply + (map :amount payments))
                            :drilldown name}))
               [])))


(defn- revenue-chart-config
  [payments {:keys [from to chart-type] :as params}]
  (let [x-axis-title (str (.format from "l") " - " (.format to "l"))
        key          (if (= "community" chart-type) :property :billed)]
    {:chart       {:type "column"}
     :title       nil
     :legend      {:enabled false}
     :subtitle    nil
     :credits     {:enabled false}
     :xAxis       {:type  "category"
                   :title {:text x-axis-title}}
     :yAxis       {:title {:text "Total Revenue"}}
     :plotOptions {:series {:borderWidth 0
                            :dataLabels  {:enabled true
                                          :format  "${point.y:.2f}"}}}
     :tooltip     {:headerFormat "<span style='font-size:11px'>{series.name}</span><br>"
                   :pointFormat  "<span style='color:{point.color}'>{point.name}</span>: <b>${point.y:.2f}</b> <br/>"}
     :series      [{:name         "Properties"
                    :colorByPoint true
                    :data         (revenue-series payments key)}]
     :drilldown   {:series (revenue-drilldown payments key)}}))


;; =============================================================================
;; Subscriptions
;; =============================================================================


(reg-sub
 ::orders
 (fn [db _]
   (db/path db)))


;; =============================================================================
;; Chart


(reg-sub
 ::revenue
 :<- [::orders]
 (fn [db _]
   (:revenue db)))


(reg-sub
 :orders.admin.chart.revenue/data
 :<- [::revenue]
 (fn [chart _]
   (:data chart)))


(reg-sub
 :orders.admin.chart.revenue/params
 :<- [::revenue]
 (fn [chart _]
   (:params chart)))


(reg-sub
 :orders.admin.chart.revenue/config
 :<- [:orders.admin.chart.revenue/data]
 :<- [:orders.admin.chart.revenue/params]
 (fn [[orders params] _]
   (revenue-chart-config orders params)))
