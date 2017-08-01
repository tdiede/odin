(ns odin.core
  (:require [odin.events]
            [odin.routes :as routes]
            [odin.subs]
            [odin.graphql]
            [odin.views.content :as content]
            [odin.account.list.views]
            [odin.account.entry.views]
            [day8.re-frame.http-fx]
            [antizer.reagent :as ant]
            [cljsjs.moment]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))


(enable-console-print!)


;; =============================================================================
;; Menu/Nav
;; =============================================================================


(defn menu-item
  [{:keys [menu/key menu/uri menu/text]}]
  [ant/menu-item
   [:a {:href (or uri (name key))}
    (or text (-> key name string/capitalize))]])


(defn side-menu []
  (let [menu-items (subscribe [:menu/items])
        items      (remove #((:menu.ui/excluded % #{}) :side) @menu-items)]
    [ant/menu {:style {:border-right "none"}}
     (map-indexed
      #(with-meta (menu-item %2) {:key %1})
      items)]))


(defn burger []
  [:div.navbar-burger.burger
   {:on-click #(dispatch [:menu/toggle])}
   [:span] [:span] [:span]])


(defn brand []
  [:div.navbar-brand
   [:a.navbar-item.brand-logo {:href "/"} "Starcity"]
   [burger]])


;; =============================================================================
;; Layout
;; =============================================================================


(defn avatar-dropdown [menu-items]
  (let [items (filter #((:menu.ui/excluded % #{}) :side) menu-items)]
    [ant/menu
     (map-indexed
      #(with-meta (menu-item %2) {:key %1})
      items)]))


(defn navbar-menu-item [{:keys [menu/key menu/uri menu/text]}]
  [:a.navbar-item {:href (or uri (name key))}
   (or text (-> key name string/capitalize))])


(defn navbar-menu []
  (let [menu-items (subscribe [:menu/items])]
    [:div.navbar-start;;.is-hidden-desktop
     (map-indexed
      #(with-meta (navbar-menu-item %2) {:key %1})
      @menu-items)]))


(defn navbar []
  (let [menu-showing (subscribe [:menu/showing?])
        menu-items   (subscribe [:menu/items])]
    [:nav.navbar.is-transparent
     [brand]

     [:div.navbar-menu
      {:class (when @menu-showing "is-active")}
      [navbar-menu]
      [:div.navbar-end;.is-hidden-touch
       [:div.navbar-item
        [ant/dropdown
         {:overlay (r/as-element (avatar-dropdown @menu-items)) :trigger ["click"]}
         [:span.flexbox.has-pointer
          [ant/avatar "DC"]
          [:span.valign.pad-left "Derryl Carter"]]]]]]]))




(defn layout []
  (let [curr-route (subscribe [:route/current])]
    [:div.container
     [navbar]
     [:section.section
      [:div.columns
       ; [:div.column.is-one-quarter.is-hidden-touch
       ;  [side-menu]]
       [:div.column
        [content/view @curr-route]]]]]))


;; =============================================================================
;; App Entry
;; =============================================================================


(defn render []
  (r/render
   [ant/locale-provider {:locale (ant/locales "en_US")}
    [layout]]
   (gdom/getElement "odin")))


(defn ^:export run []
  (routes/hook-browser-navigation!)
  (rf/dispatch-sync [:app/init])
  (render))
