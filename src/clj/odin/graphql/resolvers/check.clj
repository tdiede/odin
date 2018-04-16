(ns odin.graphql.resolvers.check
  (:require [blueprints.models.check :as check]
            [odin.teller :as teller]
            [teller.payment :as tpayment]
            [teller.check :as tcheck]
            [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]
            [datomic.api :as d]
            [blueprints.models.payment :as payment]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn payment
  "The payment that this `check` belongs to."
  [_ _ check]
  (tcheck/payment check))


(defn amount
  "The amount of this `check`."
  [_ _ check]
  (tcheck/amount check))


(defn name
  "The name of the person who wrote this `check`."
  [_ _ check]
  (tcheck/name check))


(defn received-on
  "The date this `check` was received."
  [_ _ check]
  (tcheck/received-on check))


(defn date
  "The date this `check` was written."
  [_ _ check]
  (tcheck/date check))


;; =============================================================================
;; Mutations
;; =============================================================================


(defn create!
  [{:keys [teller]}
   {{:keys [payment amount name received_date check_date bank number]} :params} _]
  (let [payment'   (tpayment/by-entity teller payment)
        check-data {:amount      amount
                    :name        name
                    :received-on received_date
                    :date        check_date
                    :bank        bank
                    :number      number}]
    (tpayment/add-check! payment' check-data)))


;; =============================================================================
;; Resolvers
;; =============================================================================


(defmethod authorization/authorized? :check/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;;fields
   :check/id          (fn [_ _ check] (tcheck/id check))
   :check/payment     payment
   :check/amount      amount
   :check/name        name
   :check/received-on received-on
   :check/date        date
   ;; mutations
   :check/create!     create!})
