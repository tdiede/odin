(ns odin.accounts.admin.list.subs
  (:require [odin.accounts.admin.list.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]
            [iface.table :as table]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.accounts.list/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :admin.accounts.list/selected-role
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :selected-role])))


(def unit-comp
  {:path [:active_license :unit :number]})


(reg-sub
 :admin.accounts/list
 :<- [:admin.accounts.list/query-params]
 :<- [:accounts]
 (fn [[params accounts] _]
   (let [compfns {:property     {:path [:property :name]}
                  :unit         {:path [:active_license :unit :number]}
                  :license_end  (assoc table/date-sort-comp :path [:active_license :ends])
                  :license_term {:path [:active_license :term]}}]
     (table/sort-rows params compfns accounts))))
