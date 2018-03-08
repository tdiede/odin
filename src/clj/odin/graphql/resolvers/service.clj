(ns odin.graphql.resolvers.service
  (:require [blueprints.models.account :as account]
            [blueprints.models.service :as service]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [blueprints.models.source :as source]))

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
  (service/query db params))


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


;; ==============================================================================
;; mutations ====================================================================
;; ==============================================================================


(defn create!
  [{:keys [conn requester]} {params :params} _]
  (let [{:keys [code name description]} params]
    @(d/transact conn [(service/create code name description params)
                       (source/create requester)])
    (d/entity (d/db conn) [:service/code code])))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :service/query [_ account _]
  (account/admin? account))


(defmethod authorization/authorized? :service/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :service/billed  billed
   ;; mutations
   :service/create! create!
   ;; queries
   :service/query   query
   :service/entry   entry})



(comment

  (com.walmartlabs.lacinia/execute odin.graphql/schema
           (venia.core/graphql-query
            {:venia.core/queries
             [[:service_create {:params {:name "Weasel Steaming"
                                         :code "pets,weasels"
                                         :desc "Let us give your weasel the royal treatment."}}
               [:id]]]})
           nil
           {:conn      odin.datomic/conn
            :requester (d/entity (d/db :conn) [:account/email "admin@test.com"])})

  )
