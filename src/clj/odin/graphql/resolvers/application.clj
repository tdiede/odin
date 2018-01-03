(ns odin.graphql.resolvers.application
  (:require [blueprints.models.account :as account]
            [blueprints.models.application :as application]
            [blueprints.models.approval :as approval]
            [blueprints.models.events :as events]
            [blueprints.models.income-file :as income-file]
            [blueprints.models.license :as license]
            [blueprints.models.source :as source]
            [clj-time.core :as t]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [toolbelt.datomic :as td]))

;; ==============================================================================
;; fields -----------------------------------------------------------------------
;; ==============================================================================


(defn account
  [_ _ application]
  (application/account application))


(defn approved-by
  [_ _ application]
  (when-let [approval (approval/by-account (application/account application))]
    (approval/approver approval)))


(defn approved-at
  [{conn :conn} _ application]
  (when (application/approved? application)
    (d/q '[:find ?t .
           :in $ ?app
           :where
           [?app :application/status :application.status/approved ?tx]
           [?tx :db/txInstant ?t]]
         (d/db conn) (td/id application))))


(defn status
  [_ _ application]
  (let [status (application/status application)]
    (if (= status :application.status/in-progress)
      :in_progress
      (keyword (name status)))))


(defn term
  [{conn :conn} _ application]
  (when-let [license (application/desired-license application)]
    (license/term license)))


(defn last-updated
  [{conn :conn} _ application]
  (->> [application
        (application/community-fitness application)
        (application/pet application)]
       (remove nil?)
       (map (partial td/updated-at (d/db conn)))
       (t/latest)))


(defn income
  [{conn :conn} _ application]
  (let [account (application/account application)]
    (income-file/by-account (d/db conn) account)))


(defn income-file-name
  [_ _ income-file]
  (last (string/split (:income-file/path income-file) #"/")))


(defn income-file-uri
  [{config :config} _ income-file]
  (str "/api/income/" (:db/id income-file)))



;; ==============================================================================
;; mutations --------------------------------------------------------------------
;; ==============================================================================


(defn approve!
  "Approve an application for membership."
  [{:keys [conn requester]} {:keys [application params]} _]
  (let [application (d/entity (d/db conn) application)
        account     (application/account application)]
    (cond
      (not (account/applicant? account))
      (resolve/resolve-as nil {:message "Cannot approve non-applicant!"})

      (not (application/submitted? application))
      (resolve/resolve-as nil {:message "Application must be in `submitted` status for approval!"})

      :otherwise
      (let [license (license/by-term (d/db conn) (:term params))
            unit    (d/entity (d/db conn) (:unit params))]
        @(d/transact conn (conj (approval/approve requester account unit license (:move_in params))
                                (events/account-approved account)
                                (source/create requester)))
        (d/entity (d/db conn) (:db/id application))))))


;; ==============================================================================
;; resolvers --------------------------------------------------------------------
;; ==============================================================================


(def resolvers
  {:application/account          account
   :application/approved-by      approved-by
   :application/approved-at      approved-at
   :application/status           status
   :application/term             term
   :application/updated          last-updated
   :application/income           income
   ;; mutations
   :application/approve!         approve!
   ;; income file
   :application.income-file/name income-file-name
   :application.income-file/uri  income-file-uri})
