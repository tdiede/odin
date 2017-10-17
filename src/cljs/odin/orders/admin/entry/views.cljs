(ns odin.orders.admin.entry.views
  (:require [antizer.reagent :as ant]
            [iface.loading :as loading]
            [iface.typography :as typography]
            [odin.orders.admin.entry.views.progress :as progress]
            [re-frame.core :refer [subscribe]]
            [toolbelt.core :as tb]))

(defn- subheader [order]
  (let [account  (get-in order [:account :name])
        property (get-in order [:property :name])]
    [:span "for " [:a account] " at " [:a property]]))


(defn- details
  [{:keys [status service desc price quantity]}]
  [:div
   [:h2 "Details"]
   [ant/card
    ]])


(defn view [{{order-id :order-id} :params}]
  (let [order      (subscribe [:order (tb/str->int order-id)])
        is-loading (subscribe [:loading? :order/fetch])]
    (if (and @is-loading (nil? @order))
      (loading/fullpage :text "Fetching order...")
      [:div
       (typography/view-header (:name @order) (subheader @order))

       [:div.columns
        [:div.column
         [progress/progress @order]]
        [:div.column
         #_[details @order]]]])))
