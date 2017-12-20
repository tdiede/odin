(ns odin.graphql.resolvers.application
  (:require [blueprints.models.account :as account]
            [blueprints.models.application :as application]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; fields -----------------------------------------------------------------------
;; ==============================================================================


(defn status
  [_ _ application]
  :in_progress)


(defn term
  [{conn :conn} _ application]
  3)


(defn last-updated
  [{conn :conn} _ application]
  (java.util.Date.))


;; ==============================================================================
;; resolvers --------------------------------------------------------------------
;; ==============================================================================


(def resolvers
  {:application/status status
   :application/term    term
   :application/updated last-updated})
