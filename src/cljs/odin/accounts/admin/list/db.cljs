(ns odin.accounts.admin.list.db
  (:require [iface.table :as table]
            [odin.routes :as routes]
            [clojure.string :as string]))

(def path ::accounts)


(def default-params
  {:selected-view "member"
   :sort-order    :asc
   :sort-by       :unit})


(def default-value
  {path {:params default-params}})


;; TODO: duplication in `odin.orders.admin.list.db`
(defn- remove-empty-vals [m]
  (reduce
   (fn [acc [k v]]
     (if (or (nil? v) (string/blank? v)) acc (assoc acc k v)))
   {}
   m))


(defmulti default-sort-params identity)

(defmethod default-sort-params :default [_] {})

(defmethod default-sort-params "member" [_]
  {:sort-order :asc
   :sort-by    :unit})

(defmethod default-sort-params "applicant" [_]
  {:sort-order :desc
   :sort-by    :submitted})

(defmethod default-sort-params "all" [_]
  {:sort-order :desc
   :sort-by    :created})


(defn update-roles [params]
  (let [role (:selected-view params)]
    (if (= role "all")
      (dissoc params :role)
      params)))


(defn params->route [params]
  (let [params' (-> (table/sort-params->query-params params)
                    ;; (update-roles)
                    (remove-empty-vals))]
    (routes/path-for :accounts/list :query-params params')))


(defn parse-query-params [params]
  (table/query-params->sort-params params))
