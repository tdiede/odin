(ns member.profile.payments.db
  (:require [member.profile.payments.sources.db :as sources]))


(def path ::payments)


(def default-value
  (merge
   {path {:loading {:payments/list false}}}
   sources/default-value))
