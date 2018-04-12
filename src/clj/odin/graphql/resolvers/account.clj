(ns odin.graphql.resolvers.account
  (:require [blueprints.models.account :as account]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.security-deposit :as deposit]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [clojure.core.async :refer [go]]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [customs.auth :as auth]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [odin.graphql.resolvers.utils :refer [error-message]]
            [odin.models.payment-source :as payment-source]
            [taoensso.timbre :as timbre]
            [toolbelt.async :refer [<!?]]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]
            [odin.util.validation :as uv]
            [teller.customer :as tcustomer]))

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


(defn short-name
  "Account's full name."
  [_ _ account]
  (str (account/first-name account) " " (account/last-name account)))


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


(defn service-source
  [{:keys [teller]} _ account]
  (try
    (let [customer (tcustomer/by-account teller account)]
      (tcustomer/source customer :payment.type/order))
    (catch Throwable t
      (timbre/error t ::service-source {:account (:db/id account)})
      (resolve/resolve-as nil {:mesage   (error-message t)
                               :err-data (ex-data t)}))))


(defn notes
  [_ _ account]
  (:account/notes account))


;; =============================================================================
;; Queries
;; =============================================================================


(defn- parse-gql-params
  [{:keys [roles] :as params}]
  (tb/assoc-when
   params
   :roles (when-some [xs roles]
            (map #(keyword "account.role" (name %)) xs))))


(defn- query-accounts
  [db params]
  (timbre/debug (parse-gql-params params))
  (->> (parse-gql-params params)
       (apply concat)
       (apply account/query db)))


(defn accounts
  "Query list of accounts."
  [{conn :conn} {params :params} _]
  (try
    (query-accounts (d/db conn) params)
    (catch Throwable t
      (timbre/error t "error querying accounts")
      (resolve/resolve-as nil {:message  (error-message t)
                               :err-data (ex-data t)}))))


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
    (if-not (uv/valid? vresult)
      (resolve/resolve-as nil {:message (first (uv/errors vresult))
                               :errors  (uv/errors vresult)})
      (do
        @(d/transact conn [(auth/change-password requester (:new_password_1 params))])
        requester))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :account/update! [_ account params]
  (or (account/admin? account) (= (:id params) (:db/id account))))


(defmethod authorization/authorized? :account/list [_ account _]
  (account/admin? account))


(defmethod authorization/authorized? :account/entry [_ account params]
  (or (account/admin? account) (= (:id params) (:db/id account))))


(defmethod authorization/authorized? :account/notes [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :account/active-license    active-license
   :account/deposit           deposit
   :account/property          property
   :account/role              role
   :person/short-name         short-name
   :person/full-name          full-name
   :account/emergency-contact emergency-contact
   :account/service-source    service-source
   :account/notes             notes
   ;; mutations
   :account/update!           update!
   :account/change-password!  change-password!
   ;; queries
   :account/list              accounts
   :account/entry             entry})
