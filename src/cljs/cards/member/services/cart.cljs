(ns cards.member.services.cart
  (:require [antizer.reagent :as ant]
            [cljsjs.moment]
            [devcards.core]
            [member.services.views :as view]
            [member.services.db :as db]
            [reagent.core :as r])
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]]))


(def sample-data
  {:id          1
   :title       "Full length mirror"
   :description "Aliquam posuere. Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."
   :price       15.0}
  )


(defcard-doc
  "This is a thing!")

(defcard-rg test-card
  "Something goes here"
  [:div
   [:h1 "This is text"]])


(defcard-rg shopping-cart
  (fn [_]
    ;; [view/test-div "testing"]
    [view/cart-item sample-data]
    ))
