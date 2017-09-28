(ns odin.graphql.resolvers.unit
  (:require [blueprints.models.unit :as unit]
            [clojure.string :as string]
            [toolbelt.core :as tb]))


;; =============================================================================
;; Fields
;; =============================================================================


(defn number
  "Room number of this room."
  [_ _ unit]
  (-> (unit/code unit)
      (string/split #"-")
      (last)
      (tb/str->int)))


;; =============================================================================
;; Resolvers
;; =============================================================================



(def resolvers
  {;;fields
   :unit/number number})
