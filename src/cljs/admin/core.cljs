(ns admin.core
  (:require [admin.events]
            [admin.subs]
            [antizer.reagent :as ant]
            [cljsjs.moment]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [re-frame.core :as rf :refer [dispatch subscribe]]))


(enable-console-print!)


;; =============================================================================
;; Menu/Nav
;; =============================================================================


(defn menu []
  [ant/menu {:style {:border-right "none"}}
   [ant/menu-item "Accounts"]
   [ant/menu-item "Properties"]
   [ant/menu-item "Services"]])


(defn burger []
  [:div.navbar-burger.burger
   {:on-click #(dispatch [:menu/toggle])}
   [:span] [:span] [:span]])


(defn brand []
  [:div.navbar-brand
   [:a.navbar-item "Starcity"]
   [burger]])


;; =============================================================================
;; Layout
;; =============================================================================


(defn avatar-dropdown []
  [ant/menu
   [ant/menu-item "Log Out"]])


(defn navbar-menu []
  [:div.navbar-start.is-hidden-desktop
   [:a.navbar-item {:href ""} "Accounts"]
   [:a.navbar-item {:href ""} "Properties"]
   [:a.navbar-item {:href ""} "Services"]
   [:a.navbar-item {:href "/logout"} "Log Out"]])


(defn navbar []
  (let [menu-showing (subscribe [:menu/showing?])]
    [:nav.navbar.is-transparent
     [brand]

     [:div.navbar-menu
      {:class (when @menu-showing "is-active")}
      [navbar-menu]
      [:div.navbar-end.is-hidden-touch
       [:div.navbar-item
        [ant/dropdown
         {:overlay (r/as-element (avatar-dropdown)) :trigger ["click"]}
         [ant/avatar]]]]]]))


(defn content []
  [:div
   [ant/card "Here's where the content will go."]])


(defn layout []
  [:div.container
   [navbar]
   [:section.section
    [:div.columns
     [:div.column.is-one-quarter.is-hidden-touch
      [menu]]
     [:div.column
      [content]]]]])


;; =============================================================================
;; App Entry
;; =============================================================================


(defn render []
  (r/render
   [ant/locale-provider {:locale (ant/locales "en_US")}
    [layout]]
   (gdom/getElement "admin")))


(defn ^:export run []
  ;; (routes/hook-browser-navigation!)
  (rf/dispatch-sync [:app/init])
  (render))
