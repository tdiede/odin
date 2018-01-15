(ns odin.services.member.views
  (:require [re-frame.core :refer [dispatch subscribe]]
            [antizer.reagent :as ant]
            [reagent.core :as r]
            [iface.typography :as typography]
            [toolbelt.core :as tb]))


(defn menu []
  [ant/menu {:mode          :horizontal
             :selected-keys ["book"]
             :on-click      tb/log}
   [ant/menu-item {:key "book"} "Book services"]
   [ant/menu-item {:key "manage"} "Manage services"]])


(defn view [route]
  [:div
   (typography/view-header "Premium Services" "Order and manage premium services.")
   [menu]
   [:div
    [ant/avatar {:class "ant-avatar-xl"}]]])
