(ns odin.seed
  (:require [blueprints.seed.accounts :as accounts]
            [blueprints.seed.orders :as orders]
            [blueprints.models.license :as license]
            [io.rkn.conformity :as cf]
            [datomic.api :as d]
            [re-frame.db :as db]
            [toolbelt.core :as tb]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))


(defn- referrals []
  (let [sources ["craigslist" "word of mouth" "video" "starcity member" "instagram"]
        total   (inc (rand-int 100))]
    (mapv
     (fn [_]
       {:db/id           (d/tempid :db.part/starcity)
        :referral/source (rand-nth sources)
        :referral/from   :referral.from/tour})
     (range total))))


(defn- rand-unit [property]
  (-> property :property/units vec rand-nth :db/id))


(defn distinct-by
  "Returns elements of xs which return unique values according to f. If multiple
  elements of xs return the same value under f, the first is returned"
  [f xs]
  (let [s (atom #{})]
    (for [x     xs
          :let  [id (f x)]
          :when (not (contains? @s id))]
      (do (swap! s conj id)
          x))))


(defn- accounts [db]
  (let [license  (license/by-term db 3)
        property (d/entity db [:property/internal-name "2072mission"])
        members  (->> (range 13)
                      (map (fn [_] (accounts/member (rand-unit property) (:db/id license))))
                      (distinct-by (comp :account/email #(tb/find-by :account/email %))))]
    (apply concat
           (accounts/member [:unit/name "52gilbert-1"] (:db/id license) :email "member@test.com")
           (accounts/admin :email "admin@test.com")
           members)))


(defn- rand-date []
  (c/to-date (t/date-time 2017 (inc (rand-int 12)) (inc (rand-int 28)))))


(defn seed [conn]
  (let [db          (d/db conn)
        accounts-tx (accounts db)
        member-ids  (->> accounts-tx
                         (filter #(and (:account/email %) (= :account.role/member (:account/role %))))
                         (map (fn [m] [:account/email (:account/email m)])))]
    (->> {:seed/accounts  {:txes [accounts-tx]}
          :seed/referrals {:txes [(referrals)]}
          :seed/orders    {:txes     [(orders/gen-orders db member-ids)]
                           :requires [:seed/accounts]}}
         (cf/ensure-conforms conn))))


;; [(->> (orders/gen-orders db member-ids)
;;       (map #(if (= 1 (rand-int 2))
;;               (assoc % :order/billed-on (rand-date))
;;               %)))]
