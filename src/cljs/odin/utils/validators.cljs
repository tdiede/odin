(ns odin.utils.validators
  (:require [odin.l10n :as l10n]
            [toolbelt.core :as tb]
            [goog.string :as gstring]
            [goog.string.format]
            [i18n.phonenumbers.PhoneNumberUtil :as pnu]
            [i18n.phonenumbers.PhoneNumberFormat :as pnf]
            [clojure.string :as string]))


(defn phone?
  [number]
  (let [pu     (pnu/getInstance)
        parsed (.parse pu number "US")]
    (.isValidNumber pu parsed)))
