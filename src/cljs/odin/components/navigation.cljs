(ns odin.components.navigation
  (:require [odin.routes :as routes]
            [toolbelt.core :as tb]))


(defn subnav
  "Renders a side-menu for navigation items within a top-level page."
  [menu-items]
  (fn []
    [:nav.panel
     (doall
      (for [[text route uri] menu-items]
        ^{:key text} [:a.panel-block {:href (routes/path-for route)}
                      text]))]))


(defn- submenu [menu-items active]
  (->> (map
        (fn [{:keys [label route]}]
          (let [href (if (string? route) route (routes/path-for route))]
            [:li [:a {:href  href
                      :class (when (= active route) "is-active")}
                  label]]))
        menu-items)
       (into [:ul.menu-list])))


(defn side-menu
  "Construct a side menu component given a data-based `menu-spec` and currently
  `active` item."
  [menu-spec active]
  (->> (mapcat
        (fn [{:keys [label children]}]
          (let [submenu (when (some? children) (submenu children active))]
            (tb/conj-when [[:p.menu-label label]] submenu)))
        menu-spec)
       (into [:aside.menu])))
