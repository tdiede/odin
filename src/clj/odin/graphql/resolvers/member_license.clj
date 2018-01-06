(ns odin.graphql.resolvers.member-license
  (:require [blueprints.models.member-license :as member-license]
            [blueprints.models.payment :as payment]
            [blueprints.models.source :as source]
            [datomic.api :as d]
            [odin.models.autopay :as autopay]
            [blueprints.models.customer :as customer]
            [toolbelt.async :refer [<!!?]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [taoensso.timbre :as timbre]
            [odin.graphql.authorization :as authorization]
            [blueprints.models.account :as account]))

;; ==============================================================================
;; fields -----------------------------------------------------------------------
;; ==============================================================================


(defn autopay-on
  "Whether or not autopay is active for this license."
  [_ _ license]
  (keyword (name (member-license/autopay-on? license))))


(defn rent-status
  "What's the status of this license owner's rent?"
  [{:keys [conn]} _ license]
  (when-some [payment (member-license/payment-within (d/db conn) license (java.util.Date.))]
    (cond
      (nil? payment)             :due
      (payment/overdue? payment) :overdue
      (payment/paid? payment)    :paid
      (payment/pending? payment) :pending
      :otherwise                 :due)))


(defn status
  "The status of the member license."
  [_ _ license]
  (keyword (name (member-license/status license))))


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


;; TODO: Authorization for `reassign!`

(defmethod authorization/authorized? :member-license/reassign! [_ account _]
  (account/admin? account))


(def resolvers
  {;; fields
   :member-license/status      status
   :member-license/autopay-on  autopay-on
   :member-license/rent-status rent-status
   ;; mutations
   :member-license/reassign!   reassign!})
