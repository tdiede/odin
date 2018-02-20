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


(defmethod content/view :services/list [route]
  [:div
   (typography/view-header "Premium Services" "Manage and view premium service offerings")
   [:div.columns
    [:div.column
     [:div.is-pulled-right
      [ant/button "Create"]]]]])


(defmethod content/view :services/entry [route]
  [:div
   (typography/view-header "Service Detail" "Let's Take a closer look at this here premium service.")])
