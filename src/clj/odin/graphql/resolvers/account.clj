(ns odin.graphql.resolvers.account
  (:require [bouncer.core :as b]
            [bouncer.validators :as v]
            [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.security-deposit :as deposit]
            [customs.auth :as auth]
            [datomic.api :as d]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [toolbelt.validation :as tv]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]))

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
  (when-let [license (member-license/active (d/db conn) account)]
    (member-license/property license)))


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



(defn- validate-password-params
  [params account]
  (letfn [(matching-password? [password]
            (auth/is-password? account password))
          (same-passwords? [_]
            (= (:new_password_1 params) (:new_password_2 params)))]
    (b/validate
     params
     {:old_password   [[v/required :message "You must enter your current password."]
                       [matching-password? :message "That is not the correct current password."]]
      :new_password_1 [[v/required :message "You must enter a new password."]
                       [v/min-count 8 :message "Your password should be at least 8 characters long."]
                       [same-passwords? :message "The passwords you entered don't match."]]})))


(defn- scrub-password-params [params]
  (tb/transform-when-key-exists params
    {:old_password   string/trim
     :new_password_1 string/trim
     :new_password_2 string/trim}))


(defn change-password!
  "Change the requesting user's password."
  [{:keys [conn requester]} params _]
  (let [params  (scrub-password-params params)
        vresult (validate-password-params params requester)]
    (if-not (tv/valid? vresult)
      (resolve/resolve-as nil {:message (first (tv/errors vresult))
                               :errors  (tv/errors vresult)})
      (do
        @(d/transact conn [(auth/change-password requester (:new_password_1 params))])
        requester))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;; fields
   :account/active-license    active-license
   :account/deposit           deposit
   :account/property          property
   :account/role              role
   :person/full-name          full-name
   :account/emergency-contact emergency-contact
   ;; mutations
   :account/update!           update!
   :account/change-password!  change-password!
   ;; queries
   :account/list              accounts
   :account/entry             entry})
