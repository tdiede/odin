(ns odin.routes.api
  (:require [odin.graphql :as graph]
            [com.walmartlabs.lacinia :refer [execute]]
            [compojure.core :as compojure :refer [defroutes GET POST]]
            [datomic.api :as d]
            [ring.util.response :as response]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn ->conn [req]
  (get-in req [:deps :conn]))


;; =============================================================================
;; Routes
;; =============================================================================


(defn extract-graphql-expression [request]
  (case (:request-method request)
    :get  [:query (get-in request [:params :query] "")]
    :post [:mutation (get-in request [:params :mutation] "")]))


(defn graphql-handler [req]
  (let [conn      (->conn req)
        [op expr] (extract-graphql-expression req)
        result    (execute graph/schema
                           (format "%s %s" (name op) expr)
                           nil
                           {:db   (d/db conn)
                            :conn conn})]
    (-> (response/response result)
        (response/content-type "application/transit+json")
        (response/status (if (-> result :errors some?) 400 200)))))


(defroutes routes
  (GET "/graphql" [] graphql-handler)
  (POST "/graphql" [] graphql-handler))
