(ns odin.graphql.resolvers.metrics
  (:require [datomic.api :as d]
            [toolbelt.core :as tb]))


(defn- query-referrals [db]
  (d/q '[:find ?s (count ?s)
         :in $
         :with ?e
         :where
         [?e :referral/source ?s]]
       db))


(defn referrals
  [{conn :conn} _ _]
  (let [referrals   (query-referrals (d/db conn))
        total-other (reduce (fn [sum [_ x]] (if (= x 1) (inc sum) sum)) 0 referrals)
        total       (reduce (fn [sum [_ x]] (+ sum x)) 0 referrals)]
    (->> (tb/conj-when referrals (when (not= total-other 0)
                                   ["other" total-other]))
         (remove #(= 1 (second %)))
         (map (fn [[label count]]
                {:label      label
                 :count      count
                 :percentage (if (zero? total)
                               0
                               (float (* 100 (/ count total))))})))))


(def resolvers
  {:metrics/referrals referrals})
