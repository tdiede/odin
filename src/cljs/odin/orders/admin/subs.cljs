(ns odin.orders.admin.subs
  (:require [odin.orders.admin.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Helpers
;; =============================================================================


;; =============================================================================
;; Chart


(defn- sum-by-service [orders]
  (->> (group-by (comp :name :service) orders)
       (reduce (fn [acc [sname orders]]
                 (conj acc [sname (apply + (map :price orders))]))
               [])))


(defn- drilldown-point [name orders]
  {:name name :id name :data (sum-by-service orders)})


(defmulti drilldown-data (fn [orders params] (:chart-type params)))

(defmethod drilldown-data "community"
  [orders params]
  (->> (group-by :property orders)
       (reduce (fn [acc [pname orders]]
                 (conj acc (drilldown-point pname orders)))
               [])))

(defmethod drilldown-data "billing"
  [orders params]
  (->> (group-by #(get-in % [:service :billed]) orders)
       (reduce (fn [acc [billed orders]]
                 (let [name (if (= billed :once) "Once" "Recurring")]
                   (conj acc (drilldown-point name orders))))
               [])))


(defn- series-point [name orders]
  {:name name :y (apply + (map :price orders)) :drilldown name})


(defmulti series-data (fn [orders params] (:chart-type params)))

(defmethod series-data "community"
  [orders params]
  (->> (group-by :property orders)
       (reduce (fn [acc [pname data]]
                 (conj acc (series-point pname data)))
               [])))

(defmethod series-data "billing" [orders params]
  (->> (group-by #(get-in % [:service :billed]) orders)
       (reduce (fn [acc [billed data]]
                 (let [name (if (= billed :once) "Once" "Recurring")]
                   (conj acc (series-point name data))))
               [])))


(defn- chart-config
  [orders {:keys [from to] :as params}]
  (let [x-axis-title (str (.format from "l") " - " (.format to "l"))]
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
                    :data         (series-data orders params)}]
     :drilldown   {:series (drilldown-data orders params)}}))


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
 ::chart
 :<- [::orders]
 (fn [db _]
   (:chart db)))


(reg-sub
 :orders.admin.chart/orders
 :<- [::chart]
 (fn [chart _]
   (:orders chart)))


(reg-sub
 :orders.admin.chart/params
 :<- [::chart]
 (fn [chart _]
   (:params chart)))


(reg-sub
 :orders.admin.chart/config
 :<- [:orders.admin.chart/orders]
 :<- [:orders.admin.chart/params]
 (fn [[orders params] _]
   (chart-config orders params)))
