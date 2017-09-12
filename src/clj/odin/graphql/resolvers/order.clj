(ns odin.graphql.resolvers.order
  (:require [blueprints.models.order :as order]))


(defn price
  [_ _ order]
  (order/computed-price order))


(defn order-name
  [_ _ order]
  (order/computed-name order))


(defn status
  [_ _ order]
  (-> order order/status name keyword))


(defn billed-on
  [_ _ order]
  ;; TODO:
  (java.util.Date.))


(def resolvers
  {:order/price     price
   :order/name      order-name
   :order/status    status
   :order/billed-on billed-on})
