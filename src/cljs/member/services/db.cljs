(ns member.services.db
  (:require [member.services.member.db :as member]))


(def path ::services)


(def default-value
  (merge
   {path {}}
   member/default-value))
