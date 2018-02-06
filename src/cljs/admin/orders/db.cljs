(ns admin.orders.db
  (:require [admin.routes :as routes]
            [clojure.string :as string]
            [iface.components.table :as table]
            [toolbelt.core :as tb]))



(def path ::orders)


(def default-params
  {:sort-by    :created
   :sort-order :desc
   :datekey    :created
   :statuses   #{:all}})


(def default-value
  {path {; list
         :params   default-params
         :accounts []}})


;; list =========================================================================


(defn params->route [params]
  (let [params (-> (table/sort-params->query-params params)
                   (tb/transform-when-key-exists
                       {:datekey    name
                        :from       #(when % (.unix %))
                        :to         #(when % (.unix %))
                        :accounts   #(->> (interpose "," %) (apply str))
                        :statuses   #(->> (map name %)
                                          (interpose ",")
                                          (apply str))})
                   (table/remove-empty-vals))]
    (routes/path-for :orders/list :query-params params)))


(defn parse-query-params [params]
  (-> (table/query-params->sort-params params)
      (tb/transform-when-key-exists
          {:datekey    keyword
           :from       #(js/moment. (* (tb/str->int %) 1000))
           :to         #(js/moment. (* (tb/str->int %) 1000))
           :accounts   #(->> (string/split % #",") (map tb/str->int))
           :statuses   #(->> (string/split % #",") (map keyword) set)})))
