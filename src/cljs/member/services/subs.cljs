(ns member.services.subs
  (:require [member.services.db :as db]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :services/query-params
 :<- [db/path]
 (fn [db _]
   (:params db)))


(reg-sub
 :services/section
 :<- [:route/current]
 (fn [{page :page} _]
   (name page)))


(reg-sub
 :services.book/categories
 :<- [db/path]
 (fn [db _]
   [{:category :all
     :label    "All"}
    {:category :room-upgrades
     :label    "Room Upgrades"}
    {:category :laundry-services
     :label    "Laundry Services"}
    {:category :pet-services
     :label    "Pet Services"}]))


(reg-sub
 :services.book/category
 :<- [db/path]
 (fn [db _]
   (get-in db [:params :category])))


(reg-sub
 :services.book.category/route
 :<- [:services/query-params]
 :<- [:route/current]
 (fn [[query-params route] [_ category]]
   (db/params->route (:page route)
                     (assoc query-params :category category))))


;; gets a category id and checks if it has more than 2 items in it
(reg-sub
 :services.book.category/has-more?
 :<- [db/path]
 (fn [db [_ id]]
   (let [catalogue (first (filter #(= (:id %) id) (:catalogues db)))]
     (> (count (:items catalogue)) 2))))


(reg-sub
 :services.add-service/adding
 :<- [db/path]
 (fn [db _]
   (:adding db)))


(reg-sub
 :services.add-service/form
 :<- [db/path]
 (fn [db _]
   (:form-data db)))


(reg-sub
 :services.add-service/visible?
 :<- [:modal/visible? db/modal]
 (fn [is-visible _]
   is-visible))


(reg-sub
 :services.add-service/required-fields
 :<- [:services.add-service/form]
 (fn [form _]
   (into [] (filter #(:required %) form))))


(reg-sub
 :services.add-service/can-submit?
 :<- [:services.add-service/form]
 :<- [:services.add-service/required-fields]
 (fn [[form required-fields] _]
   (reduce (fn [all-defined field]
             (and all-defined (:value field))) true required-fields)))


(reg-sub
 :services.book/catalogues
 :<- [db/path]
 (fn [db _]
   (:catalogues db)))


;; THOUGHT should this be called "cart" instead?
(reg-sub
 :services.cart/cart
 :<- [db/path]
 (fn [db _]
   (:cart db)))


(reg-sub
 :services.cart/item-count
 :<- [:services.cart/cart]
 (fn [db _]
   (count db)))

;; NOTE when testing be aware that some items and their respective service ids are repeated in
;;      test data. If we test adding those, the cost of every repeat will be added to the total cost

;; Is there a better way to write this? I feel like all these nested reduces are cringe worthy
(reg-sub
 :services.cart/total-cost
 :<- [:services.cart/cart]
 (fn [cart _]
   (reduce #(+ %1 (:price %2)) 0 cart)))
