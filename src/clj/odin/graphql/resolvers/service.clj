(ns odin.graphql.resolvers.service
  (:require [blueprints.models.account :as account]
            [blueprints.models.service :as service]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [taoensso.timbre :as timbre]
            [blueprints.models.source :as source]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [clojure.set :as set]))

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


;; ==============================================================================
;; mutations ====================================================================
;; ==============================================================================


(defn- parse-service-field-option
  [{:keys [value index]}]
  (service/create-option value value {:index index}))


(defn- parse-service-field
  [{:keys [index type label required options]}]
  (service/create-field label type
                        {:index    index
                         :required required
                         :options  (map parse-service-field-option options)}))


(defn- parse-mutate-params
  [params]
  (tb/transform-when-key-exists params
    {:billed   #(keyword "service.billed" (name %))
     :catalogs (partial map #(if (string? %) (keyword %) %))
     :fields   (partial map parse-service-field)}))


#_(comment

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
      (parse-mutate-params))

  )


(defn create!
  [{:keys [conn requester]} {params :params} _]
  (let [{:keys [code name description]} params]
    @(d/transact conn [(service/create code name description (parse-mutate-params params))
                       (source/create requester)])
    (d/entity (d/db conn) [:service/code code])))


(defn delete!
  [{:keys [conn requester]} {:keys [service]} _]
  (timbre/info (str "attempting to delete service id " service))
  @(d/transact conn [[:db.fn/retractEntity service]
                     (source/create requester)])
  :ok)


(defn- update-field-option-tx*
  [db {:keys [id index label] :as params}]
  (let [e (d/entity db id)]
    (cond-> []
      (not= (:service-field-option/index e) index)
      (conj [:db/add id :service-field-option/index index])

      (not= (:service-field-option/label e) label)
      (conj [:db/add id :service-field-option/label label]
            [:db/add id :service-field-option/value label]))))


(defn- update-field-options-tx
  [db field existing-options options-params]
  (let [[new old]    ((juxt remove filter) (comp some? :id) options-params)
        existing-ids (set (map :db/id existing-options))
        update-ids   (set (map :id old))
        to-retract   (set/difference existing-ids update-ids)]
    (cond-> []
      (not (empty? to-retract))
      (concat (map #(vector :db.fn/retractEntity %) to-retract))

      (not (empty? old))
      (concat (remove empty? (mapcat (partial update-field-option-tx* db) old)))

      (not (empty? new))
      (concat (map (fn [{:keys [label index]}]
                     {:db/id                 (:db/id field)
                      :service-field/options (service/create-option label label {:index index})})
                   new)))))


(defn- update-field-tx
  [db {:keys [id label index required options]}]
  (let [e (d/entity db id)]
    (cond-> []
      (not= label (:service-field/label e))
      (conj [:db/add id :service-field/label label])

      (not= index (:service-field/index e))
      (conj [:db/add id :service-field/index index])

      (not= required (:service-field/required required))
      (conj [:db/add id :service-field/required required])

      (and (some? options) (not (empty? options)))
      (concat (update-field-options-tx db e (:service-field/options e) options)))))


(defn update-service-fields-tx
  [db service fields-params]
  (let [fields       (service/fields service)
        [new old]    ((juxt remove filter) (comp some? :id) fields-params)
        existing-ids (set (map :db/id fields))
        update-ids   (set (map :id old))
        to-retract   (set/difference existing-ids update-ids)]
    (cond-> []
      (not (empty? to-retract))
      (concat (map #(vector :db.fn/retractEntity %) to-retract))

      (not (empty? old))
      (concat (remove empty? (mapcat (partial update-field-tx db) old)))

      (not (empty? new))
      (concat (map (fn [{:keys [label type] :as field}]
                     {:db/id          (:db/id service)
                      :service/fields (service/create-field label type field)})
                   new)))))



(defn edit-service-tx
  [existing updated]
  (timbre/info "\n\n ====================current service name is: "(:service/name existing))
  (let [id (td/id existing)]
    (cond-> []

      ;; special case - there's a name-internal attribute for some reason.
      (and (not= (:service/name existing) (:name updated)) (not (nil? (:name updated))))
      (conj [:db/add id :service/name (:name updated)])

      ;; special case - it's `desc` in datomic, but `description` everywhere else
      (and (not= (:service/desc-internal existing) (:description updated)) (not (nil? (:description updated))))
      (conj [:db/add id :service/desc-internal (:description updated)])

      ;; default case
      #_(and (not= (:service/code existing) (:code updated)) (not (nil? (:code updated))))
      #_(conj [{:db/id        (td/id existing)
                :service/code (:code updated)}])

      ;; default case
      #_(and (not= (:service/price existing) (:price updated)) (not (nil? (:price updated))))
      #_(conj [{:db/id (td/id existing)
                :service/price (:price updated)}])

      ;; default case
      #_(and (not= (:service/cost existing) (:cost updated)) (not (nil? (:cost updated))))
      #_(conj [{:db/id (td/id existing)
                :service/cost (:cost existing)}])

      ;; special case - the billed keyword values are namespaced in datomic, but not in the client
      #_(and (not= (name (:service/billed existing)) (name (:billed updated))) (not (nil? (:billed updated))))
      #_(conj [{:db/id (td/id existing)
                :service/billed (keyword "service.billed" (name (:billed updated)))}])

      ;; default case
      #_(and (not= (:service/rental existing) (:rental updated)) (not (nil? (:rental updated))))
      #_(conj [{:db/id (td/id existing)
                :service/rental (:rental updated)}])

      ;; default case
      #_(and (not= (:service/active existing) (:active updated) (not (nil? (:active updated)))))
      #_(conj [{:db/id (td/id existing)
                :service/active (:active updated)}])

      ;; catalogs - existing is a set of keywords. updated is a list of keywords.
      ;; also we need to make sure it removes thigns that were not there. THIS CURRENTLY DOES NOT DO THAT.
      #_(and (not= (:service/catalogs existing) (set (:catalogs updated))) (not (nil? (:catalogs updated))))
      #_(conj [{:db/id (td/id existing)
                :service/catalogs (set (:catalogs updated))}])

      ;; properties - existing has a set of lookup refs (by property id). updated is a vector of property ids.
      ;; also we need to make sure it removes thigns that were not there. THIS CURRENTLY DOES NOT DO THAT.
      #_(and (not= (set (map td/id (:service/properties existing))) (set (map td/id (:properties updated)))))
      #_(conj [{:db/id (td/id existing)
                :service/properties (set (map td/id (:properties updated)))}])

      ;; fields - handled by their own special function

      )))

(comment

  (def conn odin.datomic/conn)

  (td/mapify (d/entity (d/db conn) 17592186046043))

  (def service (d/entity (d/db conn) 17592186046043))

  (->> {:id          17592186046043
        :name        "Weaselish Steaming"
        :description "Let's steam some weasels."
        :code        "test,edits"
        :catalogs    '(:pets :storage)
        :properties  [285873023222987]
        :price       15.0
        :cost        3.0
        :billed      :once}
       (edit-service-tx service))


  (->> [{:id    17592186046048
         :index 0
         :label "blah blah"}
        {:index    2
         :label    "This is a date field"
         :type     :date
         :required false}
        {:id      17592186046049
         :index   1
         :label   "These are options."
         :options [{:id    17592186046050
                    :index 0
                    :label "aa"}
                   ;; {:id    17592186046051
                   ;;  :index 2
                   ;;  :label "bb"}
                   {:id    17592186046052
                    :index 1
                    :label "cc"}
                   {:id    17592186046054
                    :index 2
                    :label "dd"}]}]
      (update-service-fields-tx (d/db conn) service))

  )


(defn update!
  [{:keys [conn requester]} {:keys [service params]} _]
  (let [service (d/entity (d/db conn) service-id)]
    (timbre/info "\n\n--------------- we updatin boyzzzzz ---------------------\n\n\n")
    @(d/transact conn (concat
                       [(edit-service-tx service params)
                        #_(service/edit service (parse-mutate-params params))
                        (source/create requester)]
                       (update-service-fields-tx (d/db conn) service (:fields params))))
    (d/entity (d/db conn) service)))

(comment
  (defn update-mock
    [service-id params]
    (timbre/info "\n\n--------------- we updatin boyzzzzz ---------------------\n")
    (let [service (d/entity (d/db conn) service-id)]
      (timbre/info "\n\n in case it's interesting, here is the service we're updating:")
      (clojure.pprint/pprint (td/mapify service))
      (timbre/info "if there's nothing above that line, we're fucked")
      @(d/transact conn (concat
                         [(edit-service-tx service params)
                          #_(source/create requester)]
                         (update-service-fields-tx (d/db conn) service (:fields params))))
      (d/entity (d/db conn) service-id)))

  (update-mock 17592186046043 {:name        "Weaselish Steaming"
                               :description "Let's steam some weasels."
                               :code        "test,edits"
                               :catalogs    '(:pets :storage)
                               :properties  [285873023222987]
                               :price       15.0
                               :cost        3.0
                               :billed      :once})


  )

;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :service/query
  [{conn :conn} account {params :params}]
  (or (account/admin? account)
      (let [property (account/current-property (d/db conn) account)]
        (= (:properties params) [(td/id property)]))))


(defmethod authorization/authorized? :service/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :service/billed  billed
   ;; mutations
   :service/create! create!
   :service/delete! delete!
   :service/update! update!
   ;; queries
   :service/query   query
   :service/entry   entry})


(comment

  ;; Run the query.
  (let [conn odin.datomic/conn]
    (com.walmartlabs.lacinia/execute
     odin.graphql/schema
     (venia.core/graphql-query
      {:venia/queries
       [[:services {:params {:q          "dog"
                             :properties [285873023222997]}}
         [:name]]]})
     nil
     {:conn      conn
      :requester (d/entity (d/db conn) [:account/email "member@test.com"])}))

  (query-services (d/db odin.datomic/conn) {:properties [[:property/code "2072mission"]]})

  (let [conn odin.datomic/conn]
    (account/current-property (d/db conn) (d/entity (d/db conn) [:account/email "member@test.com"])))


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
