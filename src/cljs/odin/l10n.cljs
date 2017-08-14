(ns odin.l10n
  "Define our strings + number formatters for internationalization.
  https://github.com/tonsky/tongue"
  (:require [tongue.core :as tongue]
            [toolbelt.core :as tb]
            [re-frame.core :refer [subscribe]]))


(def dicts
  {:en {;; Specify the number formatter to use.
        :tongue/format-number (tongue/number-formatter {:group   ","
                                                        :decimal "."})

        ;; NOTE: Cannot nest keys indefinitely. We'll need a function if we want
        ;; to go more than one level deep.
        :people {:admin  "People"
                 :member "Neighbors"}

        :communities {:admin  "Communities"
                      :member "Community"}

        :home {:admin  "Home"
               :member "Activity"}

        :profile {:admin  "Profile"
                  :member "Profile"}

        ;; See above note--the below won't actually work.
        ;; One idea for role polymorphism
        ;; THING -> Variants -> Role
        :community {:one   {:admin  "Property"
                            :member "Community"}
                    :other {:admin  "Properties"
                            :member "Communities"}}

        :property   "Property"
        :properties "Properties"

        :unit  "Unit"
        :units "Units"

        :rent "Rent"
        :term "Term"

        :note {:one   "Note"
               :other "Notes"}

        ;; Payment stuff
        :view-on-stripe "View transaction on Stripe."}

   :tongue/fallback :en})


(def lookup-in-dict
  (tongue/build-translate dicts))


(defn locale
  "Retrieves the current user locale."
  []
  @(subscribe [:language]))


(defn translate [& args]
  (apply lookup-in-dict (locale) args))
