(ns odin.accounts.admin.list.db
  (:require [iface.table :as table]
            [odin.routes :as routes]
            [clojure.string :as string]))

(def path ::accounts)


(def default-params
  {:selected-role "member"})


(def default-value
  {path {:params default-params}})


;; TODO: duplication in `odin.orders.admin.list.db`
(defn- remove-empty-vals [m]
  (reduce
   (fn [acc [k v]]
     (if (or (nil? v) (string/blank? v)) acc (assoc acc k v)))
   {}
   m))


(defn params->route [params]
  (let [params' (-> (table/sort-params->query-params params)
                    (remove-empty-vals))]
    (routes/path-for :accounts/list :query-params params')))


(defn parse-query-params [params]
  (table/query-params->sort-params params))
