(ns odin.graphql.resolvers.service
  (:require [blueprints.models.service :as service]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]
            [com.walmartlabs.lacinia.resolve :as resolve]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn billed
  [_ _ service]
  (-> (service/billed service) name keyword))


;; =============================================================================
;; Queries
;; =============================================================================


(defn- query-services
  [db params]
  (->> (apply concat params)
       (apply service/query db)))


(defn query
  "Query services."
  [{conn :conn} {params :params} _]
  (try
    (query-services (d/db conn) params)
    (catch Throwable t
      (timbre/error t "error querying services")
      (resolve/resolve-as nil {:message  (str "Exception:" (.getMessage t))
                               :err-data (ex-data t)}))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {:service/billed billed
   :service/query  query})
