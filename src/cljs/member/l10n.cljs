(ns member.l10n
  (:require [iface.utils.l10n :as l10n]
            [tongue.core :as tongue]))


;; ==============================================================================
;; l10n =========================================================================
;; ==============================================================================


(def dicts
  {:en {:profile    "profile"
        :community  "Community"
        :property   "Property"
        :properties "Properties"
        :unit       "Unit"
        :units      "Units"
        :rent       "Rent"
        :term       "Term"
        :deposit    "Security Deposit"

        :note {:one   "Note"
               :other "Notes"}

        ;; profile section
        :membership      "Membership"
        :contact-info    "Contact Info"
        :settings        "Settings"
        :change-password "Change Password"
        :log-out         "Log Out"

        ;; Payments
        :payments            "Payments"
        :sources             "Payment Methods"
        :history             "History"
        :payment-sources     "Payment Methods"
        :payment-history     "Payment History"
        :payment-history-for "Payment History for {1}"
        :transaction-history "Transaction History"
        :btn-unlink-account  "Unlink this account"
        :btn-add-new-account "Add Payment Method"}

   :tongue/fallback :en})


(def lookup
  (tongue/build-translate dicts))


(defn translate [& args]
  (apply lookup l10n/*language* args))
