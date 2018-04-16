(ns odin.graphql.resolvers.member-license
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [blueprints.models.source :as source]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [datomic.api :as d]
            [odin.graphql.authorization :as authorization]
            [odin.models.autopay :as autopay]
            [taoensso.timbre :as timbre]
            [teller.customer :as tcustomer]
            [teller.payment :as tpayment]
            [toolbelt.async :refer [<!!?]]
            [toolbelt.date :as date]
            [teller.property :as tproperty]
            [teller.subscription :as tsubscription]
            [clj-time.core :as t]))

;; ==============================================================================
;; helpers ======================================================================
;; ==============================================================================


(defn- license-customer
  "Given a member's `license`, produce the teller customer."
  [teller license]
  (tcustomer/by-account teller (member-license/account license)))


;; ==============================================================================
;; fields -----------------------------------------------------------------------
;; ==============================================================================


(defn autopay-on
  "Whether or not autopay is active for this license."
  [{teller :teller} _ license]
  (let [customer (license-customer teller license)]
    (some? (tsubscription/query teller {:customers [customer]
                                        :payment-types   [:payment.type/rent]}))))


(defn- payment-within
  [teller license date]
  (let [customer (license-customer teller license)
        tz       (t/time-zone-for-id (tproperty/timezone (tcustomer/property customer)))
        from     (date/beginning-of-month date tz)
        to       (date/end-of-month date tz)]
    (first
     (tpayment/query teller {:customers     [customer]
                             :payment-types [:payment.type/rent]
                             :from          from
                             :to            to}))))


(defn rent-status
  "What's the status of this license owner's rent?"
  [{teller :teller} _ license]
  (when-some [payment (payment-within teller license (java.util.Date.))]
    (cond
      (tpayment/due? payment)     :due
      (tpayment/pending? payment) :pending
      (tpayment/paid? payment)    :paid
      (tpayment/overdue? payment) :overdue
      :otherwise                  :due)))


(defn status
  "The status of the member license."
  [_ _ license]
  (keyword (name (member-license/status license))))


(defn- rent-payments
  "All rent payments made by the owner of this license."
  [{teller :teller} _ license]
  (tpayment/query teller {:customers     [(license-customer teller license)]
                          :payment-types [:payment.type/rent]}))


;; ==============================================================================
;; mutations --------------------------------------------------------------------
;; ==============================================================================


(defn reassign!
  "Reassign a the member with license `license` to a new `unit`."
  [{:keys [conn stripe requester]} {{:keys [license unit rate]} :params} _]
  (let [license-before (d/entity (d/db conn) license)]
    @(d/transact conn [{:db/id               license
                        :member-license/rate rate
                        :member-license/unit unit}
                       (source/create requester)])
    (try
      (let [license-after (d/entity (d/db conn) license)]
        (when (and (not= rate (member-license/rate license-before))
                   (member-license/autopay-on? license-before))
          (let [account  (member-license/account license-before)
                customer (customer/by-account (d/db conn) account)]
            (<!!? (autopay/turn-off-autopay! conn stripe license-after (customer/bank-token customer)))
            (<!!? (autopay/turn-on-autopay! conn stripe (d/entity (d/db conn) license) (customer/bank-token customer)))))
        (d/entity (d/db conn) license))
      (catch Throwable t
        (timbre/error t ::reassign-room {:license license :unit unit :rate rate})
        (resolve/resolve-as nil {:message "Failed to completely reassign room! Likely to do with autopay..."})))))


;; ==============================================================================
;; resolvers --------------------------------------------------------------------
;; ==============================================================================


(defmethod authorization/authorized? :member-license/reassign! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :member-license/status        status
   :member-license/autopay-on    autopay-on
   :member-license/rent-payments rent-payments
   :member-license/rent-status   rent-status
   ;; mutations
   :member-license/reassign!     reassign!})
