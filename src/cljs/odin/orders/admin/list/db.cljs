(ns odin.orders.admin.list.db
  (:require [clojure.string :as string]
            [toolbelt.core :as tb]
            [odin.routes :as routes]))


(def path ::orders)


(def default-value
  {path {:params {:sort-by    :created
                  :sort-order :desc
                  :datekey    :created
                  :statuses   #{:all}}}})

(defn- remove-nil-keys [m]
  (reduce
   (fn [acc [k v]]
     (if (nil? v) acc (assoc acc k v)))
   {}
   m))


(defn params->route [params]
  (let [params (-> (tb/transform-when-key-exists
                       params
                     {:sort-by    name
                      :sort-order name
                      :datekey    name
                      :from       #(when % (.unix %))
                      :to         #(when % (.unix %))
                      :statuses   #(->> (map name %)
                                        (interpose ",")
                                        (apply str))})
                   (remove-nil-keys))]
    (routes/path-for :orders :query-params params)))


(defn parse-query-params [params]
  (tb/transform-when-key-exists params
    {:sort-by    keyword
     :sort-order keyword
     :datekey    keyword
     :from       #(js/moment. (* (tb/str->int %) 1000))
     :to         #(js/moment. (* (tb/str->int %) 1000))
     :statuses   #(->> (string/split % #",") (map keyword) set)}))
