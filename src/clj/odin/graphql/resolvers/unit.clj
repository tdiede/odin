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

(comment
  (number nil nil {:unit/name "52gilbert-27"})

  )
