(ns odin.graphql.resolvers.account
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [datomic.api :as d]
            [toolbelt.datomic :as td]
            [taoensso.timbre :as timbre]
            [blueprints.models.security-deposit :as deposit]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn active-license
  "Active member license for this account."
  [{conn :conn} _ account]
  (member-license/active (d/db conn) account))


(defn deposit
  [_ _ account]
  (deposit/by-account account))


(defn emergency-contact
  [_ _ account]
  (account/emergency-contact account))


(defn full-name
  "Account's full name."
  [_ _ account]
  (account/full-name account))


(defn property
  "The property that this account (member) is a part of."
  [{conn :conn} _ account]
  (-> (member-license/active (d/db conn) account)
      (member-license/property)))


(defn role
  [_ _ account]
  (keyword (name (account/role account))))


;; =============================================================================
;; Queries
;; =============================================================================


(defn arg-role->role [arg-role]
  (if (#{:all} arg-role)
    [:account.role/admin
     :account.role/member
     :account.role/onboarding
     :account.role/applicant]
    [(keyword "account.role" (name arg-role))]))


(defn accounts
  "Query list of accounts."
  [{conn :conn} args _]
  (->> (d/q '[:find [?e ...]
              :in $ [?role ...]
              :where
              [?e :account/email _]
              [?e :account/role ?role]]
            (d/db conn) (arg-role->role (:role args)))
       (apply td/entities (d/db conn))))


(defn entry
  "Query a single account."
  [{conn :conn} {id :id} _]
  (d/entity (d/db conn) id))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn- gen-emergency-contact-update
  [account {:keys [first_name last_name middle_name phone] :as data}]
  (clojure.pprint/pprint data)
  (letfn [(-tx [init]
            (tb/assoc-when
             init
             :person/first-name first_name
             :person/last-name last_name
             :person/middle-name middle_name
             :person/phone-number phone))]
    (when (some? data)
      (if-let [ent (account/emergency-contact account)]
        (-tx {:db/id (td/id ent)})
        (-tx {})))))


(defn- gen-update
  [account data]
  (let [{:keys [first_name last_name middle_name phone emergency_contact]} data]
    (tb/assoc-when
     {:db/id (td/id account)}
     :person/first-name first_name
     :person/last-name last_name
     :person/middle-name middle_name
     :person/phone-number phone
     :account/emergency-contact (gen-emergency-contact-update account emergency_contact))))


(defn update!
  "Update the account identified by `id` with `data`."
  [{conn :conn} {:keys [id data]} _]
  (let [tx @(d/transact conn [(gen-update (d/entity (d/db conn) id) data)])]
    (d/entity (:db-after tx) id)))
