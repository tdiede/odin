(ns odin.graphql.resolvers.check
  (:require [blueprints.models.check :as check]
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
  [{:keys [requester conn]} {{:keys [payment amount name received_date check_date]} :params} _]
  (let [check (check/create2 name amount check_date received_date)]
(tpayment/create! customer amount type {:check check})



    ))


(defmethod authorization/authorized? :check/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :check/payment payment
   ;; mutations
   :check/create! create!})
