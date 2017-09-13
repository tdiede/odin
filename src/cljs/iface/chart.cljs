(ns iface.chart
  (:require [reagent.core :as r]
            [cljsjs.highcharts]
            [cljsjs.highcharts-css]
            [cljsjs.highcharts.modules.drilldown]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Theme Setup
;; =============================================================================


(def chart-theme
  {:chart
   {:style {:fontFamily "'Work Sans', 'Fira Sans', sans-serif"}}})


(set! js/Highcharts.theme (clj->js chart-theme))


(.setOptions js/Highcharts js/Highcharts.theme)


;; =============================================================================
;; Component
;; =============================================================================


(defn chart [config]
  (let [chart (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (reset! chart (js/Highcharts.Chart. (r/dom-node this) (clj->js config))))
      :component-did-update
      (fn [this]
        (when-let [c @chart]
          (.destroy c))
        (reset! chart (js/Highcharts.Chart. (r/dom-node this) (clj->js (r/props this)))))
      :reagent-render
      (fn [config]
        [:div])})))
