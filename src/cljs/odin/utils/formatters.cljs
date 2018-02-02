(ns odin.utils.formatters
  (:require [odin.l10n :as l10n]
            [toolbelt.core :as tb]
            [goog.string :as gstring]
            [goog.string.format]
            [i18n.phonenumbers.PhoneNumberUtil :as pnu]
            [i18n.phonenumbers.PhoneNumberFormat :as pnf]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]))

;; TIMES
(defn moment [arg] (.moment js/window arg))


(defn date-short-num
  "Short date numerical (e.g. 01/01/2017)"
  [thing]
  (if thing (-> (moment thing)
                (.format (l10n/translate :tongue/format-date-short-num)))))

(defn date-month-day
  "Short date (e.g. Jan 1st)"
  [thing]
  (if thing (-> (moment thing)
                (.format (l10n/translate :tongue/format-date-month-day)))))


(defn date-short
  "Short date (e.g. Jan 01, 2017)"
  [thing]
  (if thing (-> (moment thing)
                (.format (l10n/translate :tongue/format-date-short)))))


(defn date-time
  "Date and time (e.g. January 1, 2017 @ 12:00 pm)"
  [thing]
  (if thing (-> (moment thing)
                (.format (l10n/translate :tongue/format-date-time)))))


(defn date-time-short
  "Date and time (e.g. Jan 1, 2017 @ 12:00 pm)"
  [thing]
  (if thing (-> (moment thing)
                (.format (l10n/translate :tongue/format-date-time-short)))))


(defn date-words
  "Verbose date (e.g. Today at 12:00pm)"
  [date]
  (when (some? date) (-> (moment date) (.calendar))))


(defn get-time-ago
  "Wraps moment's .fromNow() function -> '1 year ago', etc."
  [thing]
  (when thing (.fromNow (.moment js/window thing))))


(defn str->timestamp
  "For mocking purposes. Generates a timestamp for strings like 'Aug 1, 2017'."
  [str] (if str (-> (moment str) (.unix) (* 1000))))


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
  [amount]
  (l10n/translate :tongue/format-number amount))


(defn- is-int [n]
  (= (mod n 1) 0))


(defn currency
  "Accepts a number, and returns a formatted currency amount according to
  current language. e.g. [1999.99] -> '$1,999.99'"
  [amount]
  (let [to-str (if (is-int amount) str #(.toFixed % 2))]
    (->> amount to-str number (str "$"))))


(defn initials
  "Given a `name`, produce the initials (first and last name)."
  [name]
  (->> (string/split name #" ") (map first) (apply str)))


(defn sstr [args]
  (->> (interpose " " [args]) (apply str)))


(def string gstring/format)


(def format gstring/format)


(defn unescape-newlines [s]
  (string/replace s #"\\n" "\n"))


(defn escape-newlines [s]
  (string/replace s #"\n" "\\n"))


(defn newlines->line-breaks [s]
  (string/replace s #"\\n|\n" "<br>"))
