(ns iface.components.form
  (:require [cljsjs.moment]
            [antizer.reagent :as ant]))


(defn number->time [n]
  (cond-> (js/moment)
    (= n (Math/floor n))    (.minute 0)
    (not= n (Math/floor n)) (.minute (* 60 (mod n 1)))
    true                    (.hour (Math/floor n))
    true                    (.second 0)))


(defn moment->hhmm
  [m]
  (.format m "h:mm A"))


(defn generate-time-range
  [start end interval]
  (let [time-range (range start end (float (/ interval 60)))]
    (map number->time time-range)))


(defn time-picker
  "TODO: Documentation"
  [{:keys [on-change start end interval placeholder]
    :or   {on-change identity
           start     9
           end       17
           interval  30}
    :as   props}]
  (let [moments        (generate-time-range start end interval)
        fmts           (map moment->hhmm moments)
        lookup         (zipmap fmts moments)
        reverse-lookup (zipmap (map str moments) fmts)]
    [ant/select (-> (assoc props :on-change (comp on-change lookup))
                    (update :value #(when-let [x %]
                                      (reverse-lookup (str x)))))
    (doall
     (for [t fmts]
       ^{:key t} [ant/select-option {:value t} t]))]))
