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

        ;;:tongue/format-date-short "MMM D, YYYY"
        :tongue/format-date-short "MM/DD/YY"
        :tongue/format-date-time  "MMMM DD, YYYY @ HH:mm a"

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
        :view-on-stripe "View transaction on Stripe."

        :payment.status/due      "Due"
        :payment.status/canceled "Canceled"
        :payment.status/paid     "Paid"
        :payment.status/pending  "Pending"
        :payment.status/failed   "Failed"

        :payment.method/stripe-charge  "Stripe Charge"
        :payment.method/stripe-invoice "Stripe Invoice"
        :payment.method/check          "Check"

        :payment.for/rent    "Rent"
        :payment.for/deposit "Security Deposit"
        :payment.for/order   "Order"

        :sources             "Sources"
        :history             "History"
        :payment-sources     "Payment Sources"
        :payment-history     "Payment History"
        :payment-history-for "Payment History for {1}"
        :transaction-history "Transaction History"
        :btn-unlink-account  "Unlink this account"
        :btn-add-new-account "Add new Payment Source"

        :autopay                "Autopay"
        :use-for-autopay        "Use this account for Autopay"
        :confirm-unlink-autopay "Are you sure you want to disable Autopay?"

        :rent-overdue-notification-body     "Your rent of {1} was due on August 1st. Want to "
        :rent-overdue-notification-body-cta "pay that now?"}

   :tongue/fallback :en})


(def lookup-in-dict
  (tongue/build-translate dicts))


(defn locale
  "Retrieves the current user locale."
  []
  @(subscribe [:language]))


(defn translate [& args]
  (apply lookup-in-dict (locale) args))
