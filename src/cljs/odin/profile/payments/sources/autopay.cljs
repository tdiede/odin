(ns odin.profile.paymens.sources.autopay
  (:require [odin.profile.payments.sources.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-sub
                                   subscribe
                                   reg-event-db
                                   reg-event-fx
                                   path debug]]
            [toolbelt.core :as tb]))

(reg-sub
 ::sources
 (fn [db _]
   (db/path db)))

;; Returns list of eligible autopay sources (:type = :bank).
(reg-sub
 :payment.sources/autopay-sources
 :<- [::sources]
 (fn [db _]
   (filter #(= (:type %) :bank) (:sources db))))

;; Returns currently selected Autopay source, if it exists.
(reg-sub
 :payment.sources/autopay-source
 :<- [::sources]
 (fn [db _]
   (first (filter #(true? (:autopay %)) (:sources db)))))

(reg-sub
 :payment.sources/autopay-on?
 :<- [::sources]
 (fn [db _]
   (not (empty? (filter #(true? (:autopay %)) (:sources db))))))


;;(def people [ { :id 1 :name "Joe" } { :id 2 :name "Fred"}])
;;
;;(defn brian-converter [person]
;;  (if (= 2 (:id person))
;;      (assoc person :name "Brian")
;;      person))
;;
;;(map brian-converter people)


(defn- disable-autopay [source]
  (update source :autopay false))
;;
;;(defn- enable-autopay [source]
;;  (assoc source :autopay false))

(reg-event-db
 :payment.sources.autopay/toggle!
 [(path db/path)]
 (fn [db [_ value]]
    (let [sources (subscribe [:payment.sources/autopay-sources])
          active  (subscribe [:payment.sources/autopay-source])]
        (tb/log @sources)
      ;;(if (true? value)
        (map disable-autopay sources))))
        ;;(map enable-autopay sources))))
