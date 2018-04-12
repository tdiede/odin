(ns iface.utils.time
  (:require [toolbelt.core :as tb]))


(defn now []
  (js/Date.))


(defn is-before-now
  "Returns `true` if the provided time is in the past, `false` if in the future."
  [time]
  (-> (js/moment time)
      (.isBefore (.now js/moment))))


(defn time-between
  "Returns the duration in (milliseconds) between two provided times. `start` defaults to now."
  ([start end]
   (-> (js/moment end) (.diff (js/moment start))))
  ([end]
   (time-between (.now js/moment) end)))


(defn days-between
  "Returns the duration (in days) between two provided times. `start` defaults to now."
  ([start end]
   (-> (js/moment end) (.diff (js/moment start) "days")))
  ([end]
   (days-between (.now js/moment) end)))


(defn is-before [first second]
  (-> (js/moment first) (.isBefore (js/moment second))))


(defn is-after [first second]
  (-> (js/moment second) (.isBefore (js/moment first))))


(defn moment->iso
  "Converts a MomentJS instance into an ISO Date String."
  [instant]
  (-> (js/moment instant)
      (.toISOString)))


(defn iso->moment
  "Converts an ISO String into a MomentJS instance."
  [instant]
  (js/moment instant))
