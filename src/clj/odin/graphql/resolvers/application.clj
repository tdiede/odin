(ns odin.graphql.resolvers.application
  (:require [blueprints.models.application :as application]
            [blueprints.models.income-file :as income-file]
            [blueprints.models.license :as license]
            [clj-time.core :as t]
            [clojure.string :as string]
            [datomic.api :as d]
            [toolbelt.datomic :as td]))

;; ==============================================================================
;; fields -----------------------------------------------------------------------
;; ==============================================================================


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
;; resolvers --------------------------------------------------------------------
;; ==============================================================================


(def resolvers
  {:application/status           status
   :application/term             term
   :application/updated          last-updated
   :application/income           income
   ;; income file
   :application.income-file/name income-file-name
   :application.income-file/uri  income-file-uri})
