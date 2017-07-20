(ns admin.core
  (:require [admin.events]
            [admin.routes :as routes]
            [admin.subs]
            [admin.views.content :as content]
            [antizer.reagent :as ant]
            [cljsjs.moment]
            [goog.dom :as gdom]
            [reagent.core :as r]
            [re-frame.core :as rf :refer [dispatch
                                          subscribe
                                          reg-sub
                                          reg-event-db]]
            [clojure.string :as string]
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
   [:a.navbar-item "Starcity"]
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
    [:div.navbar-start.is-hidden-desktop
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
      [:div.navbar-end.is-hidden-touch
       [:div.navbar-item
        [ant/dropdown
         {:overlay (r/as-element (avatar-dropdown @menu-items)) :trigger ["click"]}
         [ant/avatar]]]]]]))


(defn layout []
  (let [curr-route (subscribe [:route/current])]
    [:div.container
     [navbar]
     [:section.section
      [:div.columns
       [:div.column.is-one-quarter.is-hidden-touch
        [side-menu]]
       [:div.column
        [content/view @curr-route]]]]]))


;; =============================================================================
;; App Entry
;; =============================================================================

(reg-event-db
 :init
 (fn [_ _]
   {:home     {:title            "Hello"
               :selected-account "Josh"}
    :accounts [{:name "Josh"}
               {:name "Derryl"}]}))

(reg-sub
 :accounts
 (fn [db _]
   (:accounts db)))


(reg-sub
 :home
 (fn [db _]
   (:home db)))


(reg-sub
 :home/title
 :<- [:home]
 :<- [:accounts]
 (fn [[home accounts] _]
   (:title home)))


(reg-event-db
 :account/select
 (fn [db [_ account]]
   (assoc-in db [:home :title] (str "Hello, " (:name account)))))


(reg-event-db
 :home.title/change
 (fn [db [_ new-title]]
   (assoc-in db [:home :title] new-title)))


(defn render-sequential
  [c xs]
  (map-indexed
   #(with-meta [c %2] {:key %1})
   xs))


(defn my-component []
  (let [title    (subscribe [:home/title "als"])
        accounts (subscribe [:accounts])]
    [:section.section
     [:div.container
      [:h1 @title]
      [:br]
      [:ul
       (render-sequential
        (fn [account]
          [:li
           [:span
            [:span (:name account)]
            [:button {:type     "button"
                      :on-click #(dispatch [:account/select account])}
             "select"]]])
        @accounts)]
      [:br]
      [:input
       {:type      "text"
        :on-change (fn [ev]
                     (let [v (.. ev -target -value)]
                       (dispatch [:home.title/change v])))}]]]))


(defn render []
  (r/render
   [ant/locale-provider {:locale (ant/locales "en_US")}
    [layout]]
   (gdom/getElement "admin")))


(defn ^:export run []
  (routes/hook-browser-navigation!)
  (rf/dispatch-sync [:app/init])
  (render))
