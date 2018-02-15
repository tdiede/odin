(ns cards.core
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]])
  (:require [devcards.core]
            [reagent.core :as r]
            [cards.admin.orders.views]
            [cards.admin.properties.views]
            [cards.member.services]
            [cards.iface.components.menu]
            [cards.member.services.cart]))


(enable-console-print!)


(defcard-doc
  "
  ## Rendering Reagent Components
  Rendering *stuff*
  "
  )


(defcard-rg test-card
  [:div {:style {:border "10px solid red" :padding "20px"}}
   [:h1 "Arbitrary Reagent"]
   [:p "and stuff"]])


(defn on-click [ratom]
  (swap! ratom update-in [:count] inc))


(defn counter1 [ratom]
  [:div "Current count: " (:count @ratom)
   [:div
    [:button {:on-click #(on-click ratom)}
     "Increment"]]])


(defcard-rg counter1
  (fn [data-atom]
    [counter1 data-atom])
  (r/atom {:count 0})
  {:inspect-data true})
