(ns odin.graphql.resolvers.check
  (:require [blueprints.models.check :as check]
            [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]
            [datomic.api :as d]
            [blueprints.models.payment :as payment]))


(defn payment
  "The payment that this check belongs to."
  [_ _ check]
  (check/payment check))


(defn create!
  [{:keys [requester conn]} {{:keys [payment amount name received_date check_date]} :params} _]
  (let [check (check/create2 name amount check_date received_date)]
    @(d/transact conn [{:db/id           payment
                        :payment/method  :payment.method/check
                        :payment/check   check
                        :payment/status  :payment.status/paid
                        :payment/paid-on received_date}])
    (:payment/check (d/entity (d/db conn) payment))))


(defmethod authorization/authorized? :check/create! [_ account _]
  (account/admin? account))


(def resolvers
  {;;fields
   :check/payment payment
   ;; mutations
   :check/create! create!})
