(ns odin.core
  (:require [odin.events]
            [odin.fx]
            [odin.routes :as routes]
            [odin.l10n :as l10n]
            [odin.subs]
            [odin.graphql]
            [odin.content :as content]
            [odin.home.views]
            [odin.global.views :as global]
            [odin.kami.views]
            [odin.metrics.views]
            [odin.orders.views]
            [odin.profile.views]
            [odin.account.list.views]
            [odin.account.entry.views]
            [odin.components.modals]
            [odin.components.notifications :as notification]
            [odin.utils.formatters :as formatters]
            [day8.re-frame.http-fx]
            [starcity.re-frame.stripe-fx]
            [antizer.reagent :as ant]
            [cljsjs.moment]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [reagent.core :as r]
            [ajax.core :refer [GET]]
            [toolbelt.re-frame.fx]
            [toolbelt.core :as tb]))


(enable-console-print!)


;; =============================================================================
;; Layout
;; =============================================================================


(defn burger []
  (let [showing (subscribe [:menu/showing?])
        items   (subscribe [:menu/items])]
    (if (empty? @items)
      [:div]
      [:div.navbar-burger.burger
       {:on-click #(dispatch [:menu/toggle])
        :class    (when @showing "is-active")}
       [:div.burger-wrap
        [:div.icon [:i.fa {:class (if @showing "fa-times" "fa-bars")}]]]])))


(defn- mobile-nav-backdrop []
  (let [showing (subscribe [:menu/showing?])]
    [:div.nav-backdrop {:on-click #(dispatch [:menu/toggle])
                        :class (when @showing "visible")}]))


(defn brand []
  [:div.navbar-brand
   [:a.navbar-item.brand-logo {:href "/"} "Starcity"]
   (burger)])


(defn navbar-menu-item
  [role {:keys [feature uri]}]
  (let [root (subscribe [:route/root])]
    [:a.navbar-item {:href  uri
                     :class (when (= feature @root) "is-active")}
     (l10n/translate (keyword (name feature) role))]))


(defn navbar-menu []
  (let [menu-items (subscribe [:menu/items])
        role       (subscribe [:config/role])]
    [:div.navbar-start
     (doall
      (map-indexed
       #(with-meta [navbar-menu-item @role %2] {:key %1})
       @menu-items))]))


(defn- nav-user-menu []
  [ant/menu
   [ant/menu-item {:key "profile-link"}
    [:a {:href (routes/path-for :profile/membership)} "My Profile"]]
   [ant/menu-item {:key "log-out"}
    [:a {:href "/logout"} "Log Out"]]])


(defn navbar []
  (let [menu-showing (subscribe [:menu/showing?])
        account      (subscribe [:account])]
    [:nav.navbar
     (brand)
     [mobile-nav-backdrop]
     [:div.navbar-menu {:class (when @menu-showing "is-active")}
      [navbar-menu]
      [:div.navbar-end
       [:div.navbar-item.hoverable
        [ant/dropdown {:trigger   ["click"]
                       :placement "bottomRight"
                       :overlay   (r/as-element [nav-user-menu])}
         [:a.ant-dropdown-link
          [:div.flexbox.has-pointer
           [ant/avatar (formatters/initials (:name @account))]
           [:span.valign.pad-left
            (:name @account)]]]]]]]]))




(defn error-view []
  [:section.hero.is-fullheight
   [:div.hero-body
    [:div.container.has-text-centered
     [:h1.is-2.title "Error!"]
     [ant/icon {:type  "close-circle"
                :style {:font-size 48 :color "red"}}]
     [:div {:style {:margin-top 24}}
      [:p.is-5.subtitle "Please check your network connection and reload this page."]]]]])


(defn layout []
  (let [curr-route (subscribe [:route/current])
        error      (subscribe [:config/error?])]
    (if @error
      [error-view]
      [:div.container
       [navbar]
       [global/messages]
       [:section.section.root-section
        [:div.columns
         [:div.column
          [content/view @curr-route]]]]])))


;; =============================================================================
;; App Entry
;; =============================================================================


(defn render []
  (r/render
   [ant/locale-provider {:locale (ant/locales "en_US")}
    [layout]]
   (gdom/getElement "odin")))


(defn reload! []
  (render)
  (accountant.core/dispatch-current!))


(defn ^:export run []
  (GET "/api/config"
       :handler (fn [config]
                  (rf/dispatch-sync [:app/init config])
                  (routes/hook-browser-navigation! config)
                  (render))
       :error-handler (fn [res]
                        (tb/error res)
                        (rf/dispatch-sync [:app/init-error])
                        (render))))
