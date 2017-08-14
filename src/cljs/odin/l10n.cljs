(ns odin.l10n
  (:require [tongue.core :as tongue]
            [odin.subs]
            [toolbelt.core :as tb]
            [re-frame.core :refer [subscribe]]))


;; Define our strings + number formatters for internationalization.
;; Docs:  https://github.com/tonsky/tongue

;; TODO: Instead of hardcoding "en", the lang key should be set somewhere in
;; the top of our application – which would then modify the below behavior.

(def dicts
  { :en {
          ;; Specify the number formatter to use.
          :tongue/format-number (tongue/number-formatter {:group   ","
                                                          :decimal "."})
          :account      "Account"
          :accounts     "Accounts"

          ;; One idea for role polymorphism
          ;; THING -> Variants -> Role
          :community {:one   {:admin  "Property"
                              :member "Community"}
                      :other {:admin  "Properties"
                              :member "Communities"}}
          ;; The advantage of this structure is that we'll be able to create keys
          ;; that resolve to a role-specific string (if one exists), but also
          ;; can fallback gracefully to a higher-level value, if the string is global.

          ;; e.g. :community/one/admin  => "Property"
          ;;      :unit/one/admin       => nil        (fallback to :unit/one => "Unit")
          ;;  ... :unit/one             => "Unit"

          :property     "Property"
          :properties   "Properties"

          :unit         "Unit"
          :units        "Units"

          :rent         "Rent"
          :term         "Term"

          :note {:one   "Note"
                 :other "Notes"}

          ;; nested maps will be unpacked into namespaced keys
          ;; this is purely for ease of dictionary writing
          :animals {:dog "Dog"  ;; => :animals/dog
                    :cat "Cat"} ;; => :animals/cat

          ;; substitutions
          :welcome "Hello, {1}!"
          :between "Value must be between {1} and {2}"

          ;; arbitrary functions
          :months (fn [x]
                    (cond
                      (zero? x) "No months"
                      (= 1 x)   "1 month"
                      :else     "{1} months")) ;; you can return string with substitutions

          ;; Payment stuff
          :view-on-stripe "View transaction on Stripe."}

    :tongue/fallback :en})

; This function looks up keys in the dict
(def lookup-in-dict
  (tongue/build-translate dicts))


(defn locale
  "Retrieves the current user locale."
  []
  @(subscribe [:language]))


; This is the function used by other views,
; and has the current lang partially applied to it.
(defn translate [& args]
  (apply lookup-in-dict (locale) args))

; (def translate
;   (let [current-lang (subscribe [:language])]
;     (partial lookup-in-dict @current-lang)))
