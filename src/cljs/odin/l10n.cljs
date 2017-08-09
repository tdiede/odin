(ns odin.l10n
  (:require [tongue.core :as tongue]
            [toolbelt.core :as tb]
            [re-frame.core :refer [reg-sub subscribe]]))


; Define our strings + number formatters for internationalization.
; Docs:  https://github.com/tonsky/tongue

; TODO: Instead of hardcoding "en", the lang key should be set somewhere in
; the top of our application – which would then modify the below behavior.

(def dicts
  { :en {
          ; Specify the number formatter to use.
          :tongue/format-number (tongue/number-formatter { :group   ","
                                                           :decimal "." })

          ;; simple keys
          :color "Color"
          :flower "Flower"

          ;; namespaced keys
          :weather/rain   "Rain"
          :weather/clouds "Clouds"

          :property     "Property"
          :properties   "Properties"

          :unit         "Unit"
          :units        "Units"

          :account      "Account"
          :accounts     "Accounts"

          ;; nested maps will be unpacked into namespaced keys
          ;; this is purely for ease of dictionary writing
          :animals { :dog "Dog"   ;; => :animals/dog
                     :cat "Cat" } ;; => :animals/cat

          ;; substitutions
          :welcome "Hello, {1}!"
          :between "Value must be between {1} and {2}"

          :months (fn [x]
                    (cond
                      (zero? x) "No months"
                      (= 1 x)   "1 month"
                      :else     "{1} months"))

          ;; arbitrary functions
          ; :count (fn [x]
          ;          (cond
          ;            (zero? x) "No items"
          ;            (= 1 x)   "1 item"
          ;            :else     "{1} items")) ;; you can return string with substitutions
        }

    :tongue/fallback :en })

; This function looks up keys in the dict
(def lookup-in-dict
  (tongue/build-translate dicts))

; This is the function used by other views,
; and has the current lang partially applied to it.
(def translate
  (let [current-lang (subscribe [:language])]
    (partial lookup-in-dict @current-lang)))
