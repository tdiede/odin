(ns cards.iface.nav.menu
  (:require [iface.nav.menu :as menu]
            [reagent.core :as r])
  (:require-macros [devcards.core :refer [defcard-doc defcard-rg]]))


(defcard-doc
  "
## Side Menu

The side menu is used to add a secondary menu for UIs with many logical
subsections.

The `side-menu` component accepts a `menu-spec` and the `active` item, which
corresponds to the `key` of an item.

```clojure
[{:label    \"Item 1\"
  :children [{:label \"Subitem 1\"
              :key   :subitem-1
              :route \"submitem1\"}]}
 {:label    \"Item 2\"
  :children [{:label \"Subitem 2\"
              :key   :subitem-2
              :route \"submitem2\"}]}]
```
")


(def ^:private side-menu-spec
  [{:label    "Item 1"
    :children [{:label "Subitem 1"
                :key   :subitem-1
                :route "#"}]}
   {:label    "Item 2"
    :children [{:label "Subitem 2"
                :key   :subitem-2
                :route "#"}]}])


(defonce side-menu-state (r/atom {:active :subitem-1}))


(defn side-menu*
  []
  [menu/side-menu side-menu-spec (:active @side-menu-state)
   :on-click #(swap! side-menu-state assoc :active %)])


(defcard-rg side-menu
  [side-menu*]
  side-menu-state
  {:inspect-data true})
