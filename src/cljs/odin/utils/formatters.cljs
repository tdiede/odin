(ns odin.utils.formatters
  (:require [odin.l10n :refer [translate]]
            [i18n.phonenumbers.PhoneNumberUtil :as pnu]
            [i18n.phonenumbers.PhoneNumberFormat :as pnf]))

(defn phone-number
  "Uses Google's libphonenumber to format the provided phone number."
  [number]
  (let [pu        (pnu/getInstance)
        num       number
        country   "US"
        parsed    (.parse pu num country)
        formatted (.format pu parsed pnf/NATIONAL)]
    (str formatted)))


(defn email-link
  "Returns an <a> element linking to the specified email address."
  [email]
  [:a
   {:href (str "mailto:" email)}
   email])


; This pipes through Tongue, so it's automatically internationalized
(defn number
  "Accepts a number, and returns a formatted string according to current language. e.g. [12345] -> '12,345'"
  [amount]
  (translate :tongue/format-number amount))


(defn currency
  "Accepts a number, and returns a formatted currency amount according to current language. e.g. [1999.99] -> '$1,999.99'"
  [amount]
  (str "$" (number amount)))
