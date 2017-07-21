(ns admin.routes.api
  (:require [compojure.core :as compojure :refer [context defroutes GET]]
            [ring.util.response :as response]))


(defn ok
  [body]
  (-> (response/response body)
      (response/content-type "application/transit+json")))


(defn accounts-list-handler [req]
  (ok {:accounts [{:account/first-name "Derryl"
                   :account/last-name  "Carter"
                   :account/email      "derryl@joinstarcity.com"}
                  {:account/first-name "Josh"
                   :account/last-name  "Lehman"
                   :account/email      "josh@joinstarcity.com"}
                  {:account/first-name "Meg"
                   :account/last-name  "Bell"
                   :account/email      "meg@joinstarcity.com"}]}))


(defroutes routes
  (GET "/accounts" [] accounts-list-handler))
