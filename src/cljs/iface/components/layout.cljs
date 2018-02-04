(ns iface.components.layout
  (:require [antizer.reagent :as ant]
            [iface.utils.formatters :as formatters]
            [reagent.core :as r]))


;; internal =====================================================================


(defn- burger [is-active on-menu-click]
  [:div.navbar-burger.burger
   {:on-click on-menu-click
    :class    (when is-active "is-active")}
   [:div.burger-wrap
    [:div.icon [:i.fa {:class (if is-active "fa-times" "fa-bars")}]]]])


(defn- mobile-nav-backdrop [is-active on-menu-click]
  [:div.nav-backdrop {:on-click on-menu-click
                      :class (when is-active "visible")}])


(defn- brand [is-active on-menu-click]
  [:div.navbar-brand
   [:a.navbar-item.brand-logo {:href "/"} "Starcity"]
   (burger is-active on-menu-click)])


(defn- navbar-menu-item
  [{:keys [name key uri]} active-item]
  [:a.navbar-item
   {:href  uri
    :class (when (= active-item key) "is-active")}
   name])


;; ==============================================================================
;; api ==========================================================================
;; ==============================================================================


(defn navbar-menu-items
  "TODO:"
  [menu-items & [active-item]]
  [:div.navbar-start
   (doall
    (map-indexed
     #(with-meta [navbar-menu-item %2 active-item] {:key %1})
     menu-items))])


(defn navbar-menu-profile
  "TODO:"
  [user-name menu]
  [:div.navbar-end
   [:div.navbar-item.hoverable
    [ant/dropdown {:trigger   ["click"]
                   :placement "bottomRight"
                   :overlay   (r/as-element menu)}
     [:a.ant-dropdown-link
      [:div.flexbox.has-pointer
       [ant/avatar (formatters/initials user-name)]
       [:span.valign.pad-left
        user-name]]]]]])


(defn navbar
  "TODO:"
  []
  (let [this                                        (r/current-component)
        {:keys [mobile-menu-showing on-menu-click]} (r/props this)]
    [:nav.navbar
     (brand mobile-menu-showing on-menu-click)
     [mobile-nav-backdrop mobile-menu-showing on-menu-click]
     (into [:div.navbar-menu {:class (when mobile-menu-showing "is-active")}]
           (r/children this))]))


(defn content
  []
  [:section.section.root-section
   [:div.columns
    (into [:div.column] (r/children (r/current-component)))]])


(defn layout
  "Top-level layout for the entire application."
  []
  (into [:div.container] (r/children (r/current-component)))
  )
