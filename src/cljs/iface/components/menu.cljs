(ns iface.components.menu
  (:require [toolbelt.core :as tb]))


(defn- submenu
  [menu-items active {:keys [on-click]}]
  (->> (map
        (fn [{:keys [label key route]}]
          (let [route'    (when-not (some? on-click) route)
                on-click' (when (some? on-click) #(on-click key))]
            [:li [:a (tb/assoc-when
                      {:class (when (= active key) "is-active")}
                      :href route'
                      :on-click on-click')
                  label]]))
        menu-items)
       (into [:ul.menu-list])))


(defn- has-active-child
  "True if menu-label has an active child. Used for applying a CSS class, for mobile styling."
  [active children]
  (not (empty? (filter #(= active (:key %)) children))))


(defn side-menu
  "Construct a side menu component given a data-based `menu-spec` and currently
  `active` item."
  [menu-spec active & {:as opts}]
  (->> (mapcat
        (fn [{:keys [label children]}]
          (let [submenu (when (some? children) (submenu children active opts))]
            (tb/conj-when [[:p.menu-label {:class (when (has-active-child active children) "has-active-child")}
                            label]]
                          submenu)))
        menu-spec)
       (into [:aside.menu])))
