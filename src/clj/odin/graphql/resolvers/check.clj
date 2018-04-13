(ns odin.graphql.resolvers.check
  (:require [blueprints.models.check :as check]
            [odin.teller :as teller]
            [teller.payment :as tpayment]
            [teller.check :as tcheck]
            [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]
            [datomic.api :as d]
            [blueprints.models.payment :as payment]))


(defn payment
  "The payment that this check belongs to."
  [_ _ check]
  (tcheck/payment check))


(defn create!
  [{:keys [teller]}
   {{:keys [payment amount name received_date check_date bank number status]} :params} _]
  (let [payment'   (tpayment/by-entity teller payment)
        check-data {:amount      amount
                    :name        name
                    :received-on received_date
                    :date        check_date
                    :bank        bank
                    :number      number}]
    (tpayment/add-check! payment' check-data)))


(defmethod authorization/authorized? :check/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :check/payment payment
   ;; mutations
   :check/create! create!})
