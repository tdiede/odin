(ns odin.accounts.admin.list.db
  (:require [toolbelt.core :as tb]
            [odin.routes :as routes]))


(def path ::accounts)


(def default-params
  {:selected-role "member"})


(def default-value
  {path {:params default-params}})


(defn params->route [params]
  ;; TODO:
  (routes/path-for :accounts/list :query-params params))


(defn parse-query-params [params]
  ;; TODO:
  params)
