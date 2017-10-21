(ns odin.orders.admin.list.subs
  (:require [odin.orders.admin.list.db :as db]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))

(reg-sub
 ::orders
 (fn [db _]
   (db/path db)))


(reg-sub
 :admin.orders/query-params
 :<- [::orders]
 (fn [db _]
   (:params db)))


(def ^:private compfns
  {:date   {:asc  #(cond
                     (and (some? %1) (some? %2))
                     (.isBefore (js/moment. %1) (js/moment. %2))

                     (and (some? %1) (nil? %2))
                     true

                     :otherwise false)
            :desc #(cond
                     (and (some? %1) (some? %2))
                     (.isAfter (js/moment. %1) (js/moment. %2))

                     (and (some? %1) (nil? %2))
                     true

                     :otherwise false)}
   :number {:asc < :desc >}})


(defn sort-compfn
  [{:keys [sort-by sort-order] :as table}]
  (-> {:price     :number
       :created   :date
       :billed_on :date}
      (get sort-by)
      (compfns)
      (get sort-order)))


(defn- sort-orders [params orders]
  (sort-by (:sort-by params) (sort-compfn params) orders))


(reg-sub
 :admin.table/orders
 :<- [:admin.orders/query-params]
 :<- [:orders]
 (fn [[params orders] _]
   (sort-orders params orders)))


(reg-sub
 :admin.orders/statuses
 (fn [db _]
   [:all :pending :placed :fulfilled :charged :canceled]))


(reg-sub
 :admin.orders.statuses/selected
 :<- [:admin.orders/query-params]
 (fn [params _]
   (:statuses params #{:all})))
