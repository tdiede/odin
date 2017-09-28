(ns odin.components.membership
  (:require [odin.l10n :as l10n]
            [odin.utils.formatters :as format]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


;; TODO: put it here (currently in membership UI view)
(defn license-card
  "User-friendly representation of a Member's community agreement."
  [license]
  [ant/card "foo"])
