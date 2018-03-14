(ns odin.graphql.resolvers.service
  (:require [blueprints.models.account :as account]
            [blueprints.models.service :as service]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [blueprints.models.source :as source]
            [toolbelt.core :as tb]))

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


(defn- parse-service-field-option
  [{:keys [value index]}]
  (service/create-option value {:index index}))


(defn- parse-service-field
  [{:keys [index type label required options]}]
  (service/create-field label type
                        {:index    index
                         :required required
                         :options  (map parse-service-field-option options)}))


(defn- parse-create-params
  [params]
  (tb/transform-when-key-exists params
    {:billed #(keyword "service.billed" (name %))
     :fields (partial map parse-service-field)}))


(comment

  (-> {:description "asdfasdfasdfasfasdf",
       :properties [285873023222997 285873023222986],
       :rental false,
       :name "asdfasdf",
       :catalogs [],
       :fields
       [{:index 0,
         :type :dropdown,
         :label "asdfasdf",
         :required true,
         :options
         [{:value "xcv", :index 0, :field_index 0}
          {:value "sdf", :index 1, :field_index 0}
          {:value "wer", :index 2, :field_index 0}]}
        {:index 1, :type :text, :label "sfasdfasdfasdf", :required false}],
       :billed :monthly,
       :code "asdf",
       :cost 50.0,
       :price 55.0}
      (parse-create-params))

  )


(defn create!
  [{:keys [conn requester]} {params :params} _]
  (let [{:keys [code name description]} params]
    (clojure.pprint/pprint params)
    @(d/transact conn [(service/create code name description (parse-create-params params))
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
