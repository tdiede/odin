(ns iface.utils.validation)

;; not currently used, as there are no definitive standards for acceptable bank
;; account or routing numbers.


(def bank-account-number
  "^(\\d+)(\\d+|-)*$")


(def bank-routing-number
  "^(\\d{9})")


(def credit-card-number
  "Accepts all major card types, with or without hyphens. if hyphens are used,
  they must be consistent.

  4012-8888-8888-1881 (valid)
  4012888888881881 (valid)
  4012-888888881881 (invalid)"
  "^(?:3[47]\\d{2}([\\ \\-]?)\\d{6}\\1\\d|(?:(?:4\\d|5[1-5]|65)\\d{2}|6011)([\\ \\-]?)\\d{4}\\2\\d{4}\\2)\\d{4}$")


(def credit-card-exp-date
  "Accepts dates formatted as 01/2017, or 01/17. If a 4-digit year is
  supplied, the first digit must be 2.

  01/2017 (VALID)
  01/2017 (VALID)
  21/2017 (INVALID)
  01/1998 (INVALID)"
  "^(0[1-9]|1[0-2])\\/?(2[0-9]{3}|[0-9]{2})$")


(def credit-card-cvv
  "Accepts a 3-4 digit number."
  "^(\\d{3,4})$")
