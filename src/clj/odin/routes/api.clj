(ns odin.routes.api
  (:require [blueprints.models.account :as account]
            [buddy.auth.accessrules :refer [restrict]]
            [customs.access :as access]
            [com.walmartlabs.lacinia :refer [execute]]
            [compojure.core :as compojure :refer [defroutes GET DELETE POST]]
            [odin.graphql :as graph]
            [odin.graphql.resolvers.utils :as gqlu]
            [odin.routes.kami :as kami]
            [odin.routes.onboarding :as onboarding]
            [odin.routes.util :refer :all]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]
            [datomic.api :as d]
            [customs.access :as access]
            [blueprints.models.order :as order]))

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


(defn result->status [{:keys [errors] :as result}]
  (cond
    (nil? errors)                                      200
    (tb/find-by #(= :unauthorized (:reason %)) errors) 403
    :otherwise                                         400))


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
          (response/status (result->status result))))))


;; =============================================================================
;; History
;; =============================================================================


(defn- query-history
  [db e]
  (d/q '[:find ?attr ?type ?v ?tx-time ?account
         :in $ ?e
         :where
         [?e ?a ?v ?t true]
         [?a :db/ident ?attr]
         [?a :db/valueType ?_type]
         [?_type :db/ident ?type]
         [?t :db/txInstant ?tx-time]
         [(get-else $ ?t :source/account false) ?account]]
       (d/history db) e))


(defn- resolve-value
  [db type value]
  (if (not= type :db.type/ref)
    value
    (let [e (d/entity db value)]
      (or (:db/ident e) value))))


(defn history
  "Produce a list of all changes to entity `e`, the instant at time in which the
  change occurred, and the user that made the change (if present)."
  [db e]
  (->> (query-history db e)
       (mapv
        (fn [[attr type value tx-time account]]
          (let [value   (resolve-value db type value)
                account (when-let [account (d/entity db account)]
                          {:id   (:db/id account)
                           :name (account/short-name account)})]
            (tb/assoc-when
             {:a attr
              :v value
              :t tx-time}
             :account account))))))


;; =============================================================================
;; Routes
;; =============================================================================


(defroutes routes

  (compojure/context "/onboarding" []
    (restrict onboarding/routes
      {:handler  {:and [access/authenticated-user (access/user-isa :account.role/onboarding)]}
       :on-error (fn [_ _] {:status 403 :body "You are not authorized."})}))

  (GET "/graphql" [] (graphql-handler graph/schema))
  (POST "/graphql" [] (graphql-handler graph/schema))

  (GET "/income/:file-id" [file-id]
       (-> (fn [req]
             (let [file (d/entity (d/db (->conn req)) (tb/str->int file-id))]
               (response/file-response (:income-file/path file))))
           (restrict {:handler {:and [access/authenticated-user
                                      (access/user-isa :account.role/admin)]}})))

  (GET "/history/:entity-id" [entity-id]
       (fn [req]
         (let [db (d/db (->conn req))]
           (-> (response/response {:data {:history (history db (tb/str->int entity-id))}})
               (response/content-type "application/transit+json")))))


  (compojure/context "/kami" [] kami/routes))
