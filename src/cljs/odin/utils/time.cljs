(ns odin.utils.time
  (:require [toolbelt.core :as tb]))


;;(def ^:private ms-per-day   86400000)
;;(def ^:private ms-per-week  604800000)
;;(def ^:private ms-per-month 2592000000)
;;(def ^:private ms-per-year  31536000000)


(defn now []
  (js/Date.))

(defn is-before-now
  "Returns `true` if the provided time is in the past, `false` if in the future."
  [time]
  (-> (js/moment time)
      (.isBefore (.now js/moment))))


(defn get-time-between
  "Returns the duration in (milliseconds) between two provided times. `start` defaults to now."
  ([start end]
   (-> (js/moment end) (.diff (js/moment start))))
  ([end]
   (get-time-between (.now js/moment) end)))


(defn is-before [first second] (-> (js/moment first) (.isBefore (js/moment second))))


(defn is-after [first second] (-> (js/moment second) (.isBefore (js/moment first))))
