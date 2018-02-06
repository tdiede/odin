(ns admin.accounts.db
  (:require [iface.components.table :as table]
            [admin.routes :as routes]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]))


(def path ::path)


(def reassign-modal-key
  ::reassign)


(def default-params
  {:selected-view "member"
   :sort-order    :asc
   :sort-by       :unit})


(def default-value
  {path {;; list
         :params default-params
         ;; entry
         :units            []
         :tab              nil
         :notes            []
         :notes-pagination {:size 5
                            :page 1}
         :editing-notes    {}
         :commenting-notes {}
         :create-form      {}
         :reassign-form    {}}})


;; entry ========================================================================


(defn allowed?
  "Is `role` allowed to navigate to `tab`?"
  [role tab]
  (boolean
   ((get {:member     #{"membership" "payments" "application" "notes"}
          :applicant  #{"application" "notes"}
          :onboarding #{"application" "payments" "notes"}}
         role #{"notes"})
    tab)))


;; list view ====================================================================


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
                    (table/remove-empty-vals))]
    (timbre/debug params params' (routes/path-for :accounts/list :query-params params'))
    (routes/path-for :accounts/list :query-params params')))


(defn parse-query-params [params]
  (table/query-params->sort-params params))
