(ns odin.utils.formatters
  (:require [i18n.phonenumbers.PhoneNumberUtil :as pnu]
            [i18n.phonenumbers.PhoneNumberFormat :as pnf]))

(defn phone-number [number]
  (let [pu        (pnu/getInstance)
        num       number
        country   "US"
        parsed    (.parse pu num country)
        formatted (.format pu parsed pnf/NATIONAL)]
    (str formatted)))
