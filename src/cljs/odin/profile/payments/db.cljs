(ns odin.profile.payments.db
  (:require [odin.profile.payments.sources.db :as sources]))


(def path ::payments)


(def default-value
  (merge
   {path {:payments []
          :loading  {:payments/list false}}}
   sources/default-value))
