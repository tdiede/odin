(ns admin.services.catalogs.views
  (:require [admin.content :as content]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [admin.routes :as routes]))


;; ==============================================================================
;; subview ======================================================================
;; ==============================================================================

(defn- controls []
  [:div.columns
   [:div.column.is-3
    [ant/form-item {:label "Property"}
     [ant/select
      {:style         {:width "100%"}
       :default-value "52gilbert"}
      [ant/select-option {:value "52gilbert"} "52 Gilbert"]
      [ant/select-option {:value "944market"} "944 Market"]
      [ant/select-option {:value "611mission"} "611 Mission"]]]]
   [:div.column.is-9.has-text-right
    [ant/button
     {:type :primary
      :icon "plus"}
     "New Service"]
    [ant/button
     {:type :primary
      :icon "plus"}
     "New Catalog"]]])



(defn- content []
  [:div.columns
   [:div.column.is-3
    [:h2 "Catalogs"]
    [ant/menu
     {:mode :vertical}
     [ant/menu-item "Pets"]
     [ant/menu-item "Laundry"]
     [ant/menu-item "Storage"]
     [ant/menu-item "Furniture"]]]
   [:div.column
    "and here will be the table"]])

(defn subview []
  [:div
   [controls]
   [content]])
