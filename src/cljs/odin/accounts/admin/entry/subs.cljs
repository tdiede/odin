(ns odin.accounts.admin.entry.subs
  (:require [odin.accounts.admin.entry.db :as db]
            [re-frame.core :refer [reg-sub]]
            [clojure.string :as string]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.accounts.entry/selected-tab
 :<- [db/path]
 (fn [db _]
   (:tab db)))


;; approval =====================================================================


(reg-sub
 :admin.accounts.entry.approval/units
 :<- [db/path]
 (fn [db _]
   (->> (:units db)
        (sort-by :number))))


;; reassignment =================================================================


(reg-sub
 :admin.accounts.entry.reassign/form-data
 :<- [db/path]
 (fn [db [_ k]]
   (let [form (:reassign-form db)]
     (if (some? k)
       (get form k)
       form))))


;; notes ========================================================================


(reg-sub
 :admin.accounts.entry.note/editing
 :<- [db/path]
 (fn [db [_ id]]
   (get-in db [:editing-notes id])))


(reg-sub
 :admin.accounts.entry.create-note/form-data
 :<- [db/path]
 (fn [db _]
   (:create-form db)))


(reg-sub
 :admin.accounts.entry/can-create-note?
 :<- [:admin.accounts.entry.create-note/form-data]
 (fn [{:keys [subject content]} _]
   (and (not (string/blank? subject))
        (not (string/blank? content)))))


(reg-sub
 :admin.accounts.entry.notes/pagination
 :<- [db/path]
 (fn [db _]
   (let [total (count (:notes db))]
     (assoc (:notes-pagination db) :total total))))


(reg-sub
 :admin.accounts.entry/notes
 :<- [db/path]
 :<- [:admin.accounts.entry.notes/pagination]
 (fn [[db {:keys [size page]}] _]
   (let [notes (:notes db)]
     (->> notes
          (drop (* (dec page) size))
          (take size)))))


(reg-sub
 :admin.accounts.entry.note/comment-form-shown?
 :<- [db/path]
 (fn [db [_ note-id]]
   (boolean (get-in db [:commenting-notes note-id :shown]))))


(reg-sub
 :admin.accounts.entry.note/comment-text
 :<- [db/path]
 (fn [db [_ note-id]]
   (get-in db [:commenting-notes note-id :text])))
