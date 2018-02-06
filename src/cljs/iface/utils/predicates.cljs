(ns iface.utils.predicates
  (:require [i18n.phonenumbers.PhoneNumberUtil :as pnu]))


(defn phone?
  [number]
  (try
    (let [pu     (pnu/getInstance)
          parsed (.parse pu number "US")]
      (.isValidNumber pu parsed))
    (catch js/Error _
      false)))
