(ns odin.components.validation)

;; BANKS
;; NOT CURRENTLY USED, AS THERE ARE NO DEFINITIVE STANDARDS FOR ACCEPTABLE BANK ACCOUNT OR ROUTING NUMBERS.
(def bank-account-number "^(\\d+)(\\d+|-)*$")
(def bank-routing-number "^(\\d{9})")


;; CREDIT CARDS
;; Accepts all major card types, with or without hyphens. If hyphens are used, they must be consistent. See below examples.
;; 4012-8888-8888-1881 (VALID)   4012888888881881 (VALID)   4012-888888881881 (INVALID)
(def credit-card-number "^(?:3[47]\\d{2}([\\ \\-]?)\\d{6}\\1\\d|(?:(?:4\\d|5[1-5]|65)\\d{2}|6011)([\\ \\-]?)\\d{4}\\2\\d{4}\\2)\\d{4}$")

;; Accepts dates formatted as "01/2017", or "01/17". If a 4-digit year is supplied, the first digit must be "2".
;; 01/2017 (VALID)   01/2017 (VALID)   21/2017 (INVALID)   01/1998 (INVALID)
(def credit-card-exp-date "^(0[1-9]|1[0-2])\\/?(2[0-9]{3}|[0-9]{2})$")

;; Accepts a 3-4 digit number.
(def credit-card-cvv "^(\\d{3,4})$")


;; BITCOIN - ensures bitcoin address is properly validated. (It may not actually exist, though.)
(def bitcoin-address "^[13][a-km-zA-HJ-NP-Z0-9]{26,33}$")
