(ns cards.admin.orders.views
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]])
  (:require [devcards.core]
            [admin.orders.views :as orders]))


(defcard-doc
  "
  ## Rendering Reagent Components
  Rendering *stuff*
  "
  )


(defcard-rg order-menu
  [orders/order-name {:name   "Test &amp; Order"
                      :rental false}]
  nil
  {:inspect-data true})


(defcard-rg order-menu-rental
  [orders/order-name {:name   "Test &amp; Order"
                      :rental true}]
  nil
  {:inspect-data true})


;; (defcard-rg order-details
;;   [orders/order-details
;;    {:payments (), :service {:id 285873023223024, :name "Room Personalization", :desc "request for a quote on arbitrary room modifications", :code "customize,room,quote", :cost nil, :billed :once, :price nil}, :request nil, :projected_fulfillment nil, :variant nil, :property {:id 285873023222979, :name "West SoMa"}, :name "Room Personalization", :created #inst "2018-02-05T23:24:22.054-00:00", :account {:id 285873023223096, :name "Ava Jackson", :service_source nil}, :summary nil, :line_items (), :status :pending, :id 285873023223378, :billed_on nil, :cost nil, :quantity nil, :fulfilled_on nil, :price nil}])
