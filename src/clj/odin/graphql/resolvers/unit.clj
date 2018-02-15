(ns odin.graphql.resolvers.unit
  (:require [blueprints.models.license :as license]
            [blueprints.models.property :as property]
            [blueprints.models.unit :as unit]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [toolbelt.core :as tb]
            [blueprints.models.source :as source]
            [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]))

;; ==============================================================================
;; fields =======================================================================
;; ==============================================================================


(defn- unit-number [unit]
  (-> (unit/code unit)
      (string/split #"-")
      (last)
      (tb/str->int)))


(defn unit-name
  "Human-friendly name of this unit."
  [_ _ unit]
  (format "Unit #%s" (unit-number unit)))


(defn number
  "Room number of this unit."
  [_ _ unit]
  (unit-number unit))


(defn occupant
  "The member currently occupying this unit."
  [{conn :conn} _ unit]
  (unit/occupied-by (d/db conn) unit))


;; ==============================================================================
;; mutations ====================================================================
;; ==============================================================================


(defn update-existing [lps term rate]
  (when-let [lp (tb/find-by (comp #{term} :license/term :license-price/license) lps)]
    {:db/id               (:db/id lp)
     :license-price/price rate}))


(defn create-new [db unit term rate]
  {:db/id         (:db/id unit)
   :unit/licenses {:license-price/price   rate
                   :license-price/license (:db/id (license/by-term db term))}})


(def allowed-term?
  #{3 6 12})


(defn set-rate!
  "Set the rate for a unit for the given term."
  [{:keys [conn requester]} {:keys [id term rate]} _]
  (when-not (allowed-term? term)
    (resolve/resolve-as nil {:message (format "'%s' is not a valid term length!")
                             :term    term}))
  (let [unit (d/entity (d/db conn) id)]
    @(d/transact conn [(or (update-existing unit term rate)
                           (create-new (d/db conn) unit term rate))
                       (source/create requester)])
    (d/entity (d/db conn) id)))


;; ==============================================================================
;; queries ======================================================================
;; ==============================================================================


(defn- available? [license-price]
  (not (false? (get-in license-price [:license-price/license :license/available]))))


(defn query
  "Query units based on `params`."
  [{conn :conn} {params :params} _]
  (if-let [p (:property params)]
    (property/units (d/entity (d/db conn) p))
    (->> (d/q '[:find [?u ...]
                :where
                [?u :unit/code _]]
              (d/db conn))
         (map (partial d/entity (d/db conn))))))


;; ==============================================================================
;; resolvers ====================================================================
;; ==============================================================================


(defmethod authorization/authorized? :unit/set-rate! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :unit/name      unit-name
   :unit/number    number
   :unit/occupant  occupant
   ;; mutations
   :unit/set-rate! set-rate!
   ;; queries
   :unit/query     query})
