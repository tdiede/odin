(ns odin.graphql.resolvers.service
  (:require [blueprints.models.account :as account]
            [blueprints.models.service :as service]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn billed
  [_ _ service]
  (-> (service/billed service) name keyword))


(defn make-billed-key
  [billed]
  (keyword "service.billed" (name billed)))


;; =============================================================================
;; Queries
;; =============================================================================


(defn- query-services
  [db params]
  (->> (tb/transform-when-key-exists params {:properties (partial map (partial d/entity db))
                                             :billed     (partial map make-billed-key)})
       (service/query db)))


(defn query
  "Query services."
  [{conn :conn} {params :params} _]
  (try
    (query-services (d/db conn) params)
    (catch Throwable t
      (timbre/error t "error querying services")
      (resolve/resolve-as nil {:message  (.getMessage t)
                               :err-data (ex-data t)}))))


(defn entry
  "Get one service by id"
  [{conn :conn} {id :id} _]
  (d/entity (d/db conn) id))


;; =============================================================================
;; Resolvers
;; =============================================================================


;; we probably don't need this for all anymore? seems like this restricts all service queries?
;; members will probably need this
(defmethod authorization/authorized? :service/query [_ account _]
  #_(account/admin? account)
  true)


(def resolvers
  {:service/billed billed
   :service/query  query
   :service/entry  entry})


(comment

  ;; Run the query.
  (com.walmartlabs.lacinia/execute odin.graphql/schema
           (venia.core/graphql-query
            {:venia/queries
             [[:services {:params {:q "dog"
                                   :properties [285873023222986]}}
               [:name]]]})
           nil
           {:conn odin.datomic/conn
            ;; :requester (d/entity (d/db :conn) [:account/email "member@test.com"])
            })

  (query-services (d/db odin.datomic/conn) {:properties [[:property/code "52gilbert"]]})

  )
