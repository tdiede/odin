(ns iface.utils.l10n
  (:require [tongue.core :as tongue]))


(def ^:dynamic *language*
  :en)


(def dicts
  {:en {:tongue/format-number          (tongue/number-formatter {:group   ","
                                                                 :decimal "."})
        :tongue/format-date-month-day  "MMMM Do"
        :tongue/format-date-short      "MMM DD, YYYY"
        :tongue/format-date-short-num  "M/D/Y"
        :tongue/format-date-time       "MMMM DD, YYYY @ h:mm a"
        :tongue/format-date-time-short "M/D/YY @ h:mm a"}})


(def lookup
  (tongue/build-translate dicts))


(defn translate [& args]
  (apply lookup *language* args))
