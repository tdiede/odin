(ns admin.services.subs
  (:require [admin.services.db :as db]
            [admin.services.orders.subs]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(defn- sort-by-name
  [services]
  (sort-by #(clojure.string/lower-case (:name %)) services))


(defn- sort-by-name-and-active
  [services]
  ;; if active is nil, group it with the falses.
  (->> (group-by :active services)
       (vals)
       (reverse)
       (mapv sort-by-name)
       (flatten)))


(reg-sub
 :services/list
 :<- [db/path]
 (fn [db _]
   (let [services (norms/denormalize db :services/norms)]
     (->> (remove #(true? (:archived %)) services)
          (sort-by-name-and-active)))))


(reg-sub
 :services/archived
 :<- [db/path]
 (fn [db _]
   (let [services (norms/denormalize db :services/norms)]
     (filter #(true? (:archived %)) services))))


(reg-sub
 :services/fees
 :<- [:services/list]
 (fn [services _]
   (filter #(= :fee (:type %)) services)))


(reg-sub
 :service-id
 :<- [db/path]
 (fn [db _]
   (:service-id db)))


(reg-sub
 :service
 :<- [db/path]
 (fn [db [_ service-id]]
   (norms/get-norm db :services/norms service-id)))


(reg-sub
 :services/range
 :<- [db/path]
 (fn [db _]
   [(:from db) (:to db)]))


(reg-sub
 :services/search-text
 :<- [db/path]
 (fn [db _]
   (:search-text db)))


(reg-sub
 :services/form
 :<- [db/path]
 (fn [db _]
   (:form db)))


(reg-sub
 :services.form/fields
 :<- [:services/form]
 (fn [form _]
   (:fields form)))


(reg-sub
 :services.form.field/is-last?
 :<- [:services.form/fields]
 (fn [fields [_ index]]
   (= index (dec (count fields)))))

(reg-sub
 :services.form.field.option/is-last?
 :<- [:services.form/fields]
 (fn [fields [_ {:keys [options]} option-index]]
   (= option-index (dec (count options)))))

(reg-sub
 :service.form.field.date/is-excluded?
 :<- [:services.form/fields]
 (fn [fields [_ index day]]
   (let [excluded (get-in fields [index :excluded_days])]
     (contains? excluded day))))


(reg-sub
 :services/is-editing
 :<- [db/path]
 (fn [db _]
   (:is-editing db)))


(reg-sub
 :services/catalogs
 :<- [:services/list]
 (fn [services _]
   (vec (distinct (mapcat :catalogs services)))))



(reg-sub
 :service.form/is-valid?
 :<- [db/path]
 (fn [db [_ k]]
   (get-in db [:form-validation k])))
