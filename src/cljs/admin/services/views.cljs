(ns admin.services.views
  (:require [admin.content :as content]
            [admin.services.db :as db]
            [admin.routes :as routes]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [taoensso.timbre :as timbre]
            [iface.typography :as typography]))

;; ====================================================
;; service list
;; ====================================================

(defn create-service-form []
  [:div
   [ant/form-item
    {:label "Service Name"
     :type "text"}
    [ant/input
     {:placeholder "Service Name"}]]
   [ant/form-item
    {:label "Description"}
    [ant/input-text-area
     {:rows 4}]]
   [ant/form-item
    {:label "Catalog"}
    [ant/select
     {:style          {:width "100%"}}
     [ant/select-option {:key 1} 1]
     [ant/select-option {:key 2} 2]
     [ant/select-option {:key 3} 3]
     [ant/select-option {:key 4} 4]
     [ant/select-option {:key 5} 5]]]
   [ant/form-item
    {:label "Price"}
    [ant/input-number
     {:defaultValue 10.00
      :formatter (fn [value] (str "$" value))}]]
   [ant/form-item
    {:label "Cost"}
    [ant/input-number
     {:defaultValue 10.00
      :formatter (fn [value] (str "$" value))}]]])

(defn create-service-modal []
  [ant/modal
   {:title    "Create Service"
    :visible  @(subscribe [:modal/visible?])
    :okText   "Save New Service"
    :onCancel #(dispatch [:modal/hide])
    ;; TODO - dispatch correct event
    :onOk     #(dispatch [:modal/hide])}

   [create-service-form]])


(defn service-list-main [] ;;receives services, which is obtained from graphql
  [:div

   [create-service-modal]

   (typography/view-header "Premium Services" "Manage and view premium service offerings")
   [:div.columns
    [:div.column
     [:div.is-pulled-right
      [ant/button
       {:type     :primary
        :icon     "plus"
        :on-click #(dispatch [:modal/show])}
       "Add New Service"]]]]

   [:div "table filter controls here"] ;;TODO - fixme

   [:div "services table here"]]) ;; TODO - fixme



;; =====================================================
;; route handlers
;; =====================================================

(defmethod content/view :services/list [route]
  ;; TODO - set up subscription and pass services down
  [service-list-main])


(defmethod content/view :services/entry [route]
  [:div
   (typography/view-header "Service Detail" "Let's Take a closer look at this here premium service.")])
