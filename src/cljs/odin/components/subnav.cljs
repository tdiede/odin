(ns odin.components.subnav
  (:require [antizer.reagent :as ant]
            [odin.routes :as routes]))


(defn subnav
  "Renders a side-menu for navigation items within a top-level page."
  [menu-items]
  (fn []
    [:nav.panel
     (doall
      (for [[text route uri] menu-items]
        ^{:key text} [:a.panel-block {:href (routes/path-for route)}
                      text]))]))
