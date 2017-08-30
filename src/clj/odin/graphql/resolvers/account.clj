(ns odin.graphql.resolvers.account
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [datomic.api :as d]
            [toolbelt.datomic :as td]
            [taoensso.timbre :as timbre]
            [blueprints.models.security-deposit :as deposit]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn active-license
  "Active member license for this account."
  [{conn :conn} _ account]
  (member-license/active (d/db conn) account))


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


(defn deposit
  [_ _ account]
  (deposit/by-account account))


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


(defn set-phone!
  [{conn :conn} {:keys [id phone]} _]
  (let [tx @(d/transact conn [{:db/id                id
                               :account/phone-number phone}])]
    (d/entity (:db-after tx) id)))
