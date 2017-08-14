(ns odin.core
  (:require [odin.events]
            [odin.routes :as routes]
            [odin.l10n :refer [translate]]
            [odin.subs]
            [odin.graphql]
            [odin.views.content :as content]
            [odin.profile.views]
            [odin.account.list.views]
            [odin.account.entry.views]
            [day8.re-frame.http-fx]
            [antizer.reagent :as ant]
            [cljsjs.moment]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [reagent.core :as r]
            [ajax.core :refer [GET]]
            [toolbelt.core :as tb]))


(enable-console-print!)


;; =============================================================================
;; Menu/Nav
;; =============================================================================


(def ^:private menu-items
  [{:feature :people
    :titles  {:admin  "People"
              :member "Neighbors"}
    :uri     (routes/path-for :account/list)}
   {:feature :communities
    :titles  {:admin  "Properties"
              :member "Communities"}
    :uri     "/communities"}
   {:feature :orders
    :titles  {:admin  "Orders"
              :member "Orders"}
    :uri     "/orders"}
   {:feature :services
    :titles  {:admin  "Services"
              :member "Services"}
    :uri     "/services"}])


(defn menu-item [role {:keys [feature titles uri]}]
  [ant/menu-item
   [:a {:href uri}
    (get titles role)]])


; (defn side-menu []
;   (let [features (subscribe [:config/features])
;         role     (subscribe [:config/role])]
;     [ant/menu {:style {:border-right "none"}}
;      (doall
;       (->> menu-items
;            (filter (comp @features :feature))
;            (map-indexed #(with-meta (menu-item @role %2) {:key %1}))))]))


;; =============================================================================
;; Layout
;; =============================================================================


; (defn burger []
;   [:div.navbar-burger.burger
;    {:on-click #(dispatch [:menu/toggle])}
;    [:span] [:span] [:span]])


(defn brand []
  [:div.navbar-brand
   [:a.navbar-item.brand-logo
    {:href "/"}
    "Starcity"]])


(defn avatar-dropdown [menu-items]
  (let [items (filter #((:menu.ui/excluded % #{}) :side) menu-items)]
    [ant/menu]))



(defn navbar-menu-item [{:keys [menu/key menu/uri menu/text]}]
  [:a.navbar-item {:href (or uri (str "/" (name key)))}
   (or text (-> key name string/capitalize))])


(defn navbar-menu []
  (let [menu-items (subscribe [:menu/items])]
    [:div.navbar-start;;.is-hidden-desktop
     (map-indexed
      #(with-meta (navbar-menu-item %2) {:key %1})
      @menu-items)]))


;; Todo: Perhaps confine search input to a global overlay that pops up (a la Notion)
;;       with the navbar merely providing an entry point to that behavior,
;;       rather than actually accepting input.
(defn navbar-search []
  [:div.navbar-item
   [ant/input-search]])


(defn navbar []
  (let [menu-showing (subscribe [:menu/showing?])
        menu-items   (subscribe [:menu/items])]
    [:nav.navbar.is-transparent
     [brand]
     [:div.navbar-menu
      {:class "is-active"}
      [navbar-menu]
      [:div.navbar-end
       [navbar-search]
       [:div.navbar-item.hoverable
        [ant/dropdown
         {:overlay (r/as-element (avatar-dropdown @menu-items)) :trigger ["click"]}
         [:a.flexbox.has-pointer {:href "/profile"}
          [ant/avatar "DC"]
          [:span.valign.pad-left "Derryl Carter"]]]]]]]))


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
       [:section.section.root-section
        [:div.columns
         ; [:div.column.is-one-quarter.is-hidden-touch
          ; [side-menu]]
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


(defn ^:export run []
  (GET "/api/config"
       :handler (fn [config]
                  (tb/log config)
                  (rf/dispatch-sync [:app/init config])
                  (routes/hook-browser-navigation! config)
                  (render))
       :error-handler (fn [res]
                        (tb/error res)
                        (rf/dispatch-sync [:app/init-error])
                        (render))))
