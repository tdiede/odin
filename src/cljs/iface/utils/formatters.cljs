(ns iface.utils.formatters
  (:require [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [i18n.phonenumbers.PhoneNumberUtil :as pnu]
            [i18n.phonenumbers.PhoneNumberFormat :as pnf]
            [iface.utils.l10n :as l10n]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn date-short-num
  "Short date numerical (e.g. 01/01/2017)"
  [s]
  (when (some? s)
    (-> (js/moment s)
        (.format (l10n/translate :tongue/format-date-short-num)))))

(defn date-month-day
  "Short date (e.g. Jan 1st)"
  [s]
  (when (some? s)
    (-> (js/moment s)
        (.format (l10n/translate :tongue/format-date-month-day)))))


(defn date-short
  "Short date (e.g. Jan 01, 2017)"
  [s]
  (when (some? s)
    (-> (js/moment s)
        (.format (l10n/translate :tongue/format-date-short)))))


(defn date-time
  "Date and time (e.g. January 1, 2017 @ 12:00 pm)"
  [s]
  (when (some? s)
    (-> (js/moment s)
        (.format (l10n/translate :tongue/format-date-time)))))


(defn date-time-short
  "Date and time (e.g. Jan 1, 2017 @ 12:00 pm)"
  [s]
  (when (some? s)
    (-> (js/moment s)
        (.format (l10n/translate :tongue/format-date-time-short)))))


(defn date-words
  "Verbose date (e.g. Today at 12:00pm)"
  [date]
  (when (some? date) (-> (js/moment date) (.calendar))))


(defn get-time-ago
  "Wraps moment's .fromNow() function -> '1 year ago', etc."
  [s]
  (when (some? s) (.fromNow (js/moment s))))


(defn str->timestamp
  "For mocking purposes. Generates a timestamp for strings like 'Aug 1, 2017'."
  [str]
  (if (some? str) (-> (js/moment str) (.unix) (* 1000))))


(defn phone-number
  "Uses Google's libphonenumber to format the provided phone number."
  [number]
  (try
    (let [pu        (pnu/getInstance)
          num       number
          country   "US"
          parsed    (.parse pu num country)
          formatted (.format pu parsed pnf/NATIONAL)]
      (str formatted))
    (catch js/Error e
      (timbre/error e)
      number)))


(defn email-link
  "Returns an <a> element linking to the specified email address."
  [email]
  [:a {:href (str "mailto:" email)} email])


;; This pipes through Tongue, so it's automatically internationalized
(defn number
  "Accepts a number, and returns a formatted string according to current
  language. e.g. [12345] -> '12,345'"
  [n]
  (l10n/translate :tongue/format-number n))


(defn currency
  "Accepts a number, and returns a formatted currency amount according to
  current language. e.g. [1999.99] -> '$1,999.99'"
  [n]
  (let [to-str (if (integer? n) str #(.toFixed % 2))]
    (->> n to-str number (str "$"))))


(defn initials
  "Given a `name`, produce the initials (first and last name)."
  [name]
  (->> (string/split name #" ") (map first) (apply str)))


(defn sstr [args]
  (->> (interpose " " [args]) (apply str)))


(defn format
  [& args]
  (apply gstring/format args))


(defn unescape-newlines [s]
  (string/replace s #"\\n" "\n"))


(defn escape-newlines [s]
  (string/replace s #"\n" "\\n"))


(defn newlines->line-breaks [s]
  (string/replace s #"\\n|\n" "<br>"))
