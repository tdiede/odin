(ns onboarding.views
  (:require [antizer.reagent :as ant]
            [clojure.string :as string]
            [onboarding.prompts.views :as prompt]
            [onboarding.routes :as routes]
            [re-frame.core :refer [dispatch subscribe]]
            [clojure.set :as set]
            [toolbelt.core :as tb]
            [iface.loading :as l]))

;; =============================================================================
;; Menu
;; =============================================================================

;; =============================================================================
;; Helpers

(defn should-show-children?
  [{:keys [children show-children] :as item}]
  (if (contains? item :show-children)
    (and (not (empty? children)) show-children)
    (not (empty? children))))

(defn requirements-satisfied?
  "When there are requirements and the requirements are not met, this menu item
  should be disabled."
  [item complete]
  (if-let [requires (:requires item)]
    (set/subset? requires complete)
    true))

;; =============================================================================
;; Components

(defn menu-item-2 [{:keys [key keypath label] :as item} active complete]
  [:li
   [:a
    {:class (str (when (= keypath active) "is-active ")
                 (when (complete keypath) "is-complete ")
                 (when-not (requirements-satisfied? item complete) "is-disabled "))
     :href  (if (requirements-satisfied? item complete) (routes/path-for keypath) "")}
    [:span {:dangerouslySetInnerHTML {:__html (or label (-> key name string/capitalize))}}]
    (when (complete keypath)
      [:span.is-pulled-right.icon.is-small [:i.fa.fa-check]])]])

(defn menu-item-1
  [{:keys [key keypath label children requires] :as item} active complete]
  [:li
   [:a
    {:class (str (when (= keypath active) "is-active ")
                 (when (complete keypath) "is-complete ")
                 (when-not (requirements-satisfied? item complete) "is-disabled "))
     :href (if (requirements-satisfied? item complete) (routes/path-for keypath) "")}
    [:span {:dangerouslySetInnerHTML {:__html (or label (-> key name string/capitalize))}}]
    (when (complete keypath)
      [:span.is-pulled-right.icon.is-small [:i.fa.fa-check]])]
   (when (should-show-children? item)
     [:ul
      (map-indexed
       #(with-meta (menu-item-2 %2 active complete) {:key %1})
       children)])])

(defn menu-item-0 [{:keys [key label children]} active complete]
  (list
   ^{:key (str key "-0")} [:p.menu-label (or label (name key))]
   ^{:key (str key "-1")} [:ul.menu-list
                           (map-indexed
                            #(with-meta (menu-item-1 %2 active complete) {:key %1})
                            children)]))

(defn menu []
  (let [items    (subscribe [:menu/items])
        active   (subscribe [:menu/active])
        complete (subscribe [:menu.items/complete])]
    [:aside.menu
     (doall
      (map-indexed
       #(with-meta (menu-item-0 %2 @active @complete) {:key %1})
       @items))]))

;; =============================================================================
;; App
;; =============================================================================

(defn app []
  (let [bootstrapping (subscribe [:app/bootstrapping?])]
    [:div.container
     (if @bootstrapping
       (l/fullpage :size :large :text "Figuring out where you left off...")
       [:div.columns
        [:div.column.is-one-quarter;.is-hidden-mobile
         [menu]]
        [:div.column
         [prompt/prompt]]])]))
