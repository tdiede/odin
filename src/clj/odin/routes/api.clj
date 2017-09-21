(ns odin.routes.api
  (:require [blueprints.models.account :as account]
            [com.walmartlabs.lacinia :refer [execute]]
            [compojure.core :as compojure :refer [defroutes GET POST]]
            [odin.graphql :as graph]
            [odin.graphql.resolvers.utils :as gqlu]
            [odin.routes.kami :as kami]
            [odin.routes.util :refer :all]
            [ring.util.response :as response]))

;; =============================================================================
;; GraphQL
;; =============================================================================


(defn extract-graphql-expression [request]
  (case (:request-method request)
    :get  [:query (get-in request [:params :query] "")]
    :post [:mutation (get-in request [:params :mutation] "")]))


(defn context [req]
  (gqlu/context
    (->conn req)
    (->requester req)
    (->stripe req)
    (->config req)))


(defn graphql-handler
  [schema]
  (fn [req]
    (let [[op expr] (extract-graphql-expression req)
          result    (execute schema
                             (format "%s %s" (name op) expr)
                             nil
                             (context req))]
     (-> (response/response result)
         (response/content-type "application/transit+json")
         (response/status (if (-> result :errors some?) 400 200))))))


;; =============================================================================
;; Config
;; =============================================================================


(def ^:private admin-config
  {:role :admin
   :features
   {:home        {}
    :profile     {}
    :people      {}
    :metrics     {}
    :communities {}
    :kami        {}
    :orders      {}
    :services    {}}})


(def ^:private member-config
  {:role :member
   :features
   {:home    {}
    :profile {}}})


(defn make-config [req]
  (let [account (->requester req)]
    (case (account/role account)
      :account.role/admin admin-config
      :account.role/member member-config)))


(defn inject-account [config account]
  (assoc config :account {:id    (:db/id account)
                          :name  (format "%s %s"
                                         (account/first-name account)
                                         (account/last-name account))
                          :email (account/email account)
                          :role  (account/role account)}))


(defn config-handler [req]
  (let [account (->requester req)
        config  (-> (make-config req) (inject-account account))]
    (-> (response/response config)
        (response/content-type "application/transit+json")
        (response/status 200))))


;; =============================================================================
;; Routes
;; =============================================================================


(defroutes routes
  (GET "/config" [] config-handler)

  (GET "/graphql" [] (graphql-handler graph/schema))
  (POST "/graphql" [] (graphql-handler graph/schema))

  (compojure/context "/kami" [] kami/routes)

  ;; (GET "/kami" [] (graphql-handler graph/kami))
  ;; (POST "/kami" [] (graphql-handler graph/kami))
  )
