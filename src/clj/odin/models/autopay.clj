(ns odin.models.autopay
  (:require [blueprints.models.account :as account]
            [blueprints.models.customer :as customer]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.property :as property]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [datomic.api :as d]
            [ribbon.connect :as rcn]
            [ribbon.customer :as rcu]
            [ribbon.plan :as rp]
            [ribbon.subscription :as rs]
            [toolbelt.async :as ta :refer [<!? go-try]]
            [toolbelt.date :as date]
            [toolbelt.datomic :as td]
            [clojure.spec.alpha :as s]
            [ribbon.core :as ribbon]
            [odin.models.payment-source :as payment-source]
            [taoensso.timbre :as timbre]))


;; other ========================================================================


(defn autopay-source
  "Fetch the autopay source for the requesting user, if there is one."
  [db stripe source]
  (let [account (payment-source/source-account db source)]
    (when-let [customer (customer/autopay db account)]
      (when-some [source-id (customer/bank-token customer)]
        (let [managed (property/rent-connect-id (customer/managing-property customer))]
          (rcu/fetch-source stripe (customer/id customer) source-id
                            :managed-account managed))))))

(s/fdef autopay-source
        :args (s/cat :db td/db? :stripe ribbon/conn? :source map?)
        :ret (s/or :chan ta/chan? :nothing nil?))


(defn is-autopay-source?
  "Is `source` used as the autopay source?"
  [db stripe source]
  (go-try
   (if-let [c (autopay-source db stripe source)]
     (let [autopay-source (<!? c)]
       [(= (:fingerprint source) (:fingerprint autopay-source)) autopay-source])
     [false nil])))


(s/fdef is-autopay-source?
        :args (s/cat :db td/db? :stripe ribbon/conn? :source map?)
        :ret ta/chan?)


;; turn on autopay ==============================================================


(defn- create-autopay-customer!
  "Create the autopay customer with `source-id` as a payment source."
  [stripe license source-id]
  (let [account (member-license/account license)
        managed (member-license/rent-connect-id license)]
    (rcu/create2! stripe (account/email account)
                  :description "autopay"
                  :managed-account managed
                  :source source-id)))


(defn- setup-autopay-customer!
  "Create an autopay customer if necessary, and attach the `source-id` to the
  autopay customer."
  [conn stripe license source-id]
  (go-try
   (let [account      (member-license/account license)
         managed      (member-license/rent-connect-id license)
         [ap-cus cus] ((juxt customer/autopay customer/by-account) (d/db conn) account)
         {token :id}  (<!? (rcn/create-bank-token! stripe (customer/id cus) source-id managed))]
     ;; if there's not an autopay customer...
     (if (nil? ap-cus)
       ;; create the autopay customer and attach the token to it
       (let [customer     (<!? (create-autopay-customer! stripe license token))
             bank-account (rcu/active-bank-account customer)]
         ;; create in db
         @(d/transact-async conn [(customer/create (:id customer) account
                                                   :bank-token (:id bank-account)
                                                   :managing-property (member-license/property license))])
         ;; produce bank account
         bank-account)
       (let [source (<!? (rcu/add-source! stripe (customer/id ap-cus) token :managed-account managed))]
         @(d/transact-async conn [(customer/add-bank-token ap-cus (:id source))])
         source)))))


(defn- plan-name [account property]
  (str (account/full-name account) "'s rent at " (property/name property)))


(defn- plan-amount [member-license]
  (int (* 100 (member-license/rate member-license))))


(defn- setup-autopay-plan!
  "Fetches the autopay plan, or creates it in the event that it doesn't yet
  exist."
  [stripe license]
  (go-try
   (let [plan-id (or (member-license/plan-id license) (str (:db/id license)))
         account (member-license/account license)
         managed (member-license/rent-connect-id license)]
     (try
       (<!? (rp/fetch stripe plan-id :managed-account managed))
       ;; results in error if no plan exists...
       (catch Throwable _
         ;; so create one
         (<!? (rp/create! stripe plan-id
                          (plan-name account (member-license/property license))
                          (plan-amount license)
                          :month
                          :descriptor "STARCITY RENT"
                          :managed-account managed)))))))


(defn- is-first-day-of-month? [date]
  (= (t/day (c/to-date-time date)) 1))


(s/fdef is-first-day-of-month?
        :args (s/cat :date inst?)
        :ret boolean?)


(defn- is-past? [date]
  (t/before? (c/to-date-time date) (t/now)))


(defn- first-day-next-month [date tz]
  (date/beginning-of-month (c/to-date (t/plus (c/to-date-time date) (t/months 1))) tz))


(defn- subscription-start-date
  [license]
  (let [commencement (member-license/commencement license)
        tz           (member-license/time-zone license)]
    (cond
      ;; The commencement date already passed, so the subscription needs to
      ;; start in the next calendar month. It's assumend that rent up until that
      ;; point is being collected with some other means.
      (is-past? commencement)               (first-day-next-month (java.util.Date.) tz)
      ;; If `commencement` is not in the past and is already on the first
      ;; day of the month, `commencement` is the plan start date.
      (is-first-day-of-month? commencement) commencement
      ;; Otherwise, it's the first day of the month following commencement.
      :otherwise                            (first-day-next-month commencement tz))))


(defn- update-subscription!
  [stripe license subs-id source-id]
  (rs/update! stripe subs-id
              :source source-id
              :managed-account (member-license/rent-connect-id license)))


(defn- create-subscription!
  [stripe license customer-id plan-id source-id]
  (rs/create! stripe customer-id plan-id
              :source source-id
              :managed-account (member-license/rent-connect-id license)
              :trial-end (c/to-epoch (subscription-start-date license))
              :fee-percent (-> license member-license/property property/ops-fee)))


(defn turn-on-autopay!
  [conn stripe license source-id]
  (let [account (member-license/account license)]
    (go-try
     (let [source (<!? (setup-autopay-customer! conn stripe license source-id))]
       (if-let [subs-id (member-license/subscription-id license)]
         ;; update existing subscription with new source
         (do
           (<!? (update-subscription! stripe license subs-id (:id source)))
           :ok)
         ;; create subscription
         (let [plan (<!? (setup-autopay-plan! stripe license))
               subs  (<!? (create-subscription! stripe license (:customer source) (:id plan) (:id source)))]
           @(d/transact-async conn [[:db/add (:db/id license) :member-license/subscription-id (:id subs)]
                                    [:db/add (:db/id license) :member-license/plan-id (:id plan)]])
           :ok))))))


;; turn off autopay =============================================================


(defn turn-off-autopay!
  [conn stripe license source-id]
  (go-try
   (let [subs-id               (member-license/subscription-id license)
         plan-id               (member-license/plan-id license)
         account               (member-license/account license)
         managed               (member-license/rent-connect-id license)
         source                (<!? (payment-source/fetch-source (d/db conn) stripe account source-id))
         [is-source ap-source] (<!? (is-autopay-source? (d/db conn) stripe source))]
     ;; Remove the source from the customer
     (if-not is-source
       (throw (ex-info "This source is not being used for autopay; cannot unset." {:license (:db/id license)
                                                                                   :source  source-id}))
       (do
         ;; delete the source from the managed account
         (<!? (rcu/delete-source! stripe (:customer ap-source) (:id ap-source) :managed-account managed))
         (<!? (rs/cancel! stripe subs-id :managed-account managed))
         (<!? (rp/delete! stripe plan-id :managed-account managed))
         @(d/transact-async conn [[:db/retract (:db/id license) :member-license/subscription-id subs-id]
                                  [:db/retract (:db/id license) :member-license/plan-id plan-id]
                                  [:db/retract [:stripe-customer/customer-id (:customer ap-source)]
                                   :stripe-customer/bank-account-token (:id ap-source)]])
         :ok)))))
