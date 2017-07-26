(ns odin.graphql.resolvers.account
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [datomic.api :as d]
            [toolbelt.datomic :as td]
            [taoensso.timbre :as timbre]))


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
  [context args _]
  (->> (d/q '[:find [?e ...]
              :in $ [?role ...]
              :where
              [?e :account/email _]
              [?e :account/role ?role]]
            (:db context) (arg-role->role (:role args)))
       (apply td/entities (:db context))))


(defn entry
  "Query a single account."
  [context {id :id} _]
  (d/entity (:db context) id))


(defn full-name
  "Account's full name."
  [_ _ account]
  (account/full-name account))


(defn property
  "The property that this account (member) is a part of."
  [context _ account]
  (-> (member-license/active (:db context) account)
      (member-license/property)))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn set-phone!
  [context {:keys [id phone]} _]
  (let [tx @(d/transact (:conn context) [{:db/id                id
                                          :account/phone-number phone}])]
    (d/entity (:db-after tx) id)))
