(ns odin.routes.kami
  (:require [compojure.core :as compojure :refer [defroutes GET POST]]
            [kami.core :as kami]
            [odin.routes.util :refer :all]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!!?]]
            [odin.config :as config]))


(defn- ->socrata [req]
  (config/socrata-app-token (->config req)))


(defn- search-addresses
  [query req]
  (-> {:data {:addresses (<!!? (kami/search-addresses (->socrata req) :query query))}}
      (response/response)
      (response/content-type "application/transit+json")))


(defn- score-address
  [eas-baseid req]
  (let [address   (first (<!!? (kami/search-addresses (->socrata req) :eas-baseid eas-baseid)))
        resources (<!!? (kami/search-resources (->socrata req) address))]
    (-> {:data {:kami (kami/rank resources)}}
        (response/response)
        (response/content-type "application/transit+json"))))


(defroutes routes
  (GET "/search" [q] #(search-addresses q %))
  (GET "/score" [addr] #(score-address addr %)))
