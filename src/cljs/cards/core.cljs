(ns cards.core
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]])
  (:require [devcards.core]
            [reagent.core :as r]
            [cards.iface.nav.menu]))


(enable-console-print!)


(defcard-doc
  "
  ## Rendering Reagent Components
  "
  )


(defcard-rg test-card
  [:div {:style {:border "10px solid blue" :padding "20px"}}
   [:h1 "Arbitrary Reagent"]
   [:p "and stuff"]])
