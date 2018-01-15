(ns odin.services.db
  (:require [odin.services.member.db :as member]))


(def path ::services)


(def default-value
  (merge
   {path {}}
   member/default-value))
