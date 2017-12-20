(ns odin.graphql.resolvers.member-license
  (:require [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]))

;; =============================================================================
;; Fields
;; =============================================================================


(defn autopay-on
  "Whether or not autopay is active for this license."
  [_ _ license]
  (keyword (name (member-license/autopay-on? license))))


(defn rent-status
  "What's the status of this license owner's rent?"
  [{:keys [conn]} _ license]
  (try
    (let [payment (member-license/current-payment (d/db conn) license)]
      (cond
        (nil? payment)             :due
        (payment/overdue? payment) :overdue
        (payment/paid? payment)    :paid
        (payment/pending? payment) :pending
        :otherwise                 :due))
    (catch Throwable t
      (resolve/resolve-as :due {:message  (.getMessage t)
                                :err-data (ex-data t)}))))


(defn status
  "The status of the member license."
  [_ _ license]
  (keyword (name (member-license/status license))))


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {;;fields
   :member-license/status      status
   :member-license/autopay-on  autopay-on
   :member-license/rent-status rent-status})
