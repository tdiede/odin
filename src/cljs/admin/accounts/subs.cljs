(ns admin.accounts.subs
  (:require [admin.accounts.db :as db]
            [clojure.string :as string]
            [iface.components.table :as table]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))



;; ==============================================================================
;; helper =======================================================================
;; ==============================================================================

(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :accounts
 :<- [db/path]
 (fn [db _]
   (norms/denormalize db :accounts/norms)))


(reg-sub
 :account
 :<- [db/path]
 (fn [db [_ account-id]]
   (norms/get-norm db :accounts/norms account-id)))


(reg-sub
 :account/application
 :<- [db/path]
 (fn [db [_ account-id]]
   (:application (norms/get-norm db :accounts/norms account-id))))



;; ==============================================================================
;; list =========================================================================
;; ==============================================================================

(reg-sub
 :accounts.list/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :accounts.list/selected-view
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :selected-view])))


(def sortfns
  {:property     {:path [:property :name]}
   :unit         {:path [:active_license :unit :number]}
   :license_end  (assoc table/date-sort-comp :path [:active_license :ends])
   :license_term {:path [:active_license :term]}
   :move_in      (assoc table/date-sort-comp :path [:application :move_in])
   :created      (assoc table/date-sort-comp :path [:application :created])
   :updated      (assoc table/date-sort-comp :path [:application :updated])
   :submitted    (assoc table/date-sort-comp :path [:application :submitted])})


(defn- sort-accounts
  [{:keys [sort-by sort-order] :as params} accounts]
  (if (or (nil? sort-by) (nil? sort-order))
    accounts
    (table/sort-rows params sortfns accounts)))


(defn- role-filter
  [{selected-view :selected-view} accounts]
  (if (= selected-view "member")
    (filter (comp some? :active_license) accounts)
    accounts))


(reg-sub
 :accounts/list
 :<- [:accounts.list/query-params]
 :<- [:accounts]
 (fn [[params accounts] _]
   (->> accounts
        (role-filter params)
        (sort-accounts params))))


;; ==============================================================================
;; entry ========================================================================
;; ==============================================================================


(reg-sub
 :accounts.entry/selected-tab
 :<- [db/path]
 (fn [db _]
   (:tab db)))


;; approval =====================================================================


(reg-sub
 :accounts.entry.approval/units
 :<- [db/path]
 (fn [db _]
   (->> (:units db)
        (sort-by :number))))


;; reassignment =================================================================


(reg-sub
 :accounts.entry.reassign/form-data
 :<- [db/path]
 (fn [db [_ k]]
   (let [form (:reassign-form db)]
     (if (some? k)
       (get form k)
       form))))


;; notes ========================================================================


(reg-sub
 :accounts.entry.note/editing
 :<- [db/path]
 (fn [db [_ id]]
   (get-in db [:editing-notes id])))


(reg-sub
 :accounts.entry.create-note/showing?
 :<- [db/path]
 (fn [db _]
   (get db :showing-create-note)))


(reg-sub
 :accounts.entry.create-note/form-data
 :<- [db/path]
 (fn [db [_ account-id]]
   (get-in db [:create-form account-id])))


(reg-sub
 :accounts.entry/can-create-note?
 :<- [db/path]
 (fn [db [_ account-id]]
   (let [{:keys [subject content]} (get-in db [:create-form account-id])]
     (and (not (string/blank? subject))
          (not (string/blank? content))))))


(reg-sub
 :accounts.entry.notes/pagination
 :<- [db/path]
 (fn [db _]
   (let [total (count (:notes db))]
     (assoc (:notes-pagination db) :total total))))


(reg-sub
 :accounts.entry/notes
 :<- [db/path]
 :<- [:accounts.entry.notes/pagination]
 (fn [[db {:keys [size page]}] _]
   (let [notes (:notes db)]
     (->> notes
          (drop (* (dec page) size))
          (take size)))))


(reg-sub
 :accounts.entry.note/comment-form-shown?
 :<- [db/path]
 (fn [db [_ note-id]]
   (boolean (get-in db [:commenting-notes note-id :shown]))))


(reg-sub
 :accounts.entry.note/comment-text
 :<- [db/path]
 (fn [db [_ note-id]]
   (get-in db [:commenting-notes note-id :text])))


;; orders =======================================================================


(reg-sub
 :account/orders
 (fn [db [_ account-id]]
   (->> (get-in db [:admin.services.orders.db/orders :orders/norms :norms])
        (map second)
        (filter #(= account-id (get-in % [:account :id])))
        (sort-by :created))))
