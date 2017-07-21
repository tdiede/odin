(ns admin.seed
  (:require [admin.datomic :refer [conn]]
            [blueprints.models.application :as app]
            [blueprints.models.approval :as approval]
            [blueprints.models.check :as check]
            [blueprints.models.license :as license]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.onboard :as onboard]
            [blueprints.models.promote :as promote]
            [blueprints.models.property :as property]
            [blueprints.models.rent-payment :as rp]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.unit :as unit]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [datomic.api :as d]
            [io.rkn.conformity :as cf]
            [toolbelt.core :as tb]
            [toolbelt.date :as date]))

;; =============================================================================
;; TX Construction
;; =============================================================================

;; =============================================================================
;; Accounts

(def password
  "bcrypt+blake2b-512$30e1776f40ee533841fcba62a0dbd580$12$2dae523ec1eb9fd91409ebb5ed805fe53e667eaff0333243")

(defn account
  [email first-name last-name phone role & [slack-handle]]
  (tb/assoc-when
   {:db/id                (d/tempid :db.part/starcity)
    :account/email        email
    :account/password     password
    :account/first-name   first-name
    :account/last-name    last-name
    :account/phone-number phone
    :account/role         role
    :account/activated    true}
   :account/slack-handle slack-handle))

(defn accounts-tx []
  [(account "test@test.com" "Applicant" "User" "2345678910" :account.role/applicant)
   (account "applicant@test.com" "Applicant" "User" "2345678910" :account.role/applicant)
   (account "member@test.com" "Member" "User" "2345678910" :account.role/member)
   (account "onboarding@test.com" "Onboarding" "User" "2345678910" :account.role/onboarding)
   (account "admin@test.com" "Admin" "User" "2345678910" :account.role/admin)
   (account "josh@joinstarcity.com" "Josh" "Lehman" "2345678910" :account.role/admin "@josh")])

;; =============================================================================
;; Approval

(defn approve
  "A more light-weight version of `starcity.models.approval/approve` that
  doesn't create `msg` and `cmd`."
  [approver approvee unit license move-in]
  [(approval/create approver approvee unit license move-in)
   ;; Change role
   {:db/id (:db/id approvee) :account/role :account.role/onboarding}
   (deposit/create approvee (int (unit/rate unit license)))
   (onboard/create approvee)
   (app/change-status (:account/application approvee)
                      :application.status/approved)])

(defn approval-tx [conn]
  (concat
   (approve
    (d/entity (d/db conn) [:account/email "admin@test.com"])
    (d/entity (d/db conn) [:account/email "member@test.com"])
    (unit/by-name (d/db conn) "52gilbert-1")
    (license/by-term (d/db conn) 3)
    (c/to-date (t/now)))
   (approve
    (d/entity (d/db conn) [:account/email "admin@test.com"])
    (d/entity (d/db conn) [:account/email "onboarding@test.com"])
    (unit/by-name (d/db conn) "2072mission-1")
    (license/by-term (d/db conn) 3)
    (c/to-date (t/plus (t/now) (t/months 1))))))

;; =============================================================================
;; Applications

(defn application
  [account-id & {:keys [address properties license move-in pet fitness status]
                 :or   {move-in (c/to-date (t/plus (t/now) (t/weeks 2)))
                        status  :application.status/in-progress}}]
  (let [id (d/tempid :db.part/starcity)]
    [{:db/id               account-id
      :account/application id}
     (tb/assoc-when {:db/id              id
                     :application/status status}
                    :application/license license
                    :application/communities properties
                    :application/address address
                    :application/move-in move-in
                    :application/has-pet (boolean pet)
                    :application/fitness fitness
                    :application/pet pet)]))

(defn applications-tx [conn]
  (concat
   (application [:account/email "test@test.com"]
                :license (:db/id (license/by-term (d/db conn) 3)))
   (application [:account/email "applicant@test.com"]
                :address {:address/country     "US"
                          :address/region      "CA"
                          :address/locality    "Oakland"
                          :address/postal-code "94611"}
                :license (:db/id (license/by-term (d/db conn) 3))
                :status :application.status/submitted
                :properties [[:property/internal-name "52gilbert"]
                             [:property/internal-name "2072mission"]]
                :move-in (date/to-utc-corrected-date (c/to-date (t/date-time 2017 7 1)) (t/time-zone-for-id "America/Los_Angeles"))
                :pet {:pet/type         :dog
                      :pet/weight       35
                      :pet/breed        "corgi"
                      :pet/daytime-care "crate"
                      :pet/demeanor     "bitey"
                      :pet/vaccines     true
                      :pet/bitten       true
                      :pet/sterile      true}
                :fitness {:fitness/experience   "Donec neque quam, dignissim in, mollis nec, sagittis eu, wisi."
                          :fitness/skills       "Donec neque quam, dignissim in, mollis nec, sagittis eu, wisi."
                          :fitness/free-time    "Donec neque quam, dignissim in, mollis nec, sagittis eu, wisi."
                          :fitness/interested   "Donec neque quam, dignissim in, mollis nec, sagittis eu, wisi."
                          :fitness/dealbreakers "Donec neque quam, dignissim in, mollis nec, sagittis eu, wisi."})
   (application [:account/email "onboarding@test.com"]
                :license (:db/id (license/by-term (d/db conn) 6))
                :status :application.status/approved
                :properties [[:property/internal-name "52gilbert"]])
   (application [:account/email "member@test.com"]
                :license (:db/id (license/by-term (d/db conn) 3))
                :status :application.status/approved
                :properties [[:property/internal-name "52gilbert"]])))

;; =============================================================================
;; Member Licenses

(defn member-licenses-tx [conn]
  (let [admin  (d/entity (d/db conn) [:account/email "admin@test.com"])
        member (d/entity (d/db conn) [:account/email "member@test.com"])]
    (remove #(contains? % :msg/uuid) (promote/promote member))))

;; =============================================================================
;; Rent Payments

(defn- date [y m d]
  (c/to-date (t/date-time y m d)))

(def check-december
  (let [start (date 2016 12 1)
        end   (date 2016 12 31)]
    (rp/create 2000.0 start end :rent-payment.status/paid
               :method rp/check
               :check (check/create "Member" 2000.0 (java.util.Date.) 1175)
               :due-date (date 2016 12 5)
               :paid-on (date 2016 12 15))))

(def check-november-other
  (rp/create 1000.0 (date 2016 11 15) (date 2016 11 30) :rent-payment.status/paid
             :method rp/other
             :due-date (date 2016 11 20)
             :paid-on (date 2016 11 19)
             :desc "bill.com"))

(defn- rent-payments-tx [conn]
  (let [license (->> (d/entity (d/db conn) [:account/email "member@test.com"])
                     (member-license/active (d/db conn)))]
    [(member-license/add-rent-payments
      license
      check-december
      check-november-other)]))

;; =============================================================================
;; Stripe Customers

(defn stripe-customers-tx []
  [{:db/id                              (d/tempid :db.part/starcity)
    :stripe-customer/account            [:account/email "member@test.com"]
    :stripe-customer/customer-id        "cus_9bzpu7sapb8g7y"
    :stripe-customer/bank-account-token "ba_19IlpVIvRccmW9nO20kCxqE5"}])

;; =============================================================================
;; Avatar

(defn avatar-tx []
  [{:db/id       (d/tempid :db.part/starcity)
    :avatar/name :system
    :avatar/url  "/assets/img/starcity-logo-black.png"}])

;; =============================================================================
;; API
;; =============================================================================

(defn seed
  "Seed the database with sample data."
  [conn]
  (cf/ensure-conforms
   conn
   {:seed/accounts          {:txes [(accounts-tx)]}
    :seed/applications      {:txes     [(applications-tx conn)]
                             :requires [:seed/accounts]}
    :seed/stripe-customers  {:txes     [(stripe-customers-tx)]
                             :requires [:seed/accounts]}
    :seed/avatar            {:txes [(avatar-tx)]}})
  ;; NOTE: These need to happen in separate transactions.
  (cf/ensure-conforms
   conn
   {:seed/approval {:txes [(approval-tx conn)]}})
  (cf/ensure-conforms
   conn
   {:seed/member-licenses {:txes [(member-licenses-tx conn)]}})
  (cf/ensure-conforms
   conn
   {:seed/rent-payments {:txes [(rent-payments-tx conn)]}}) )
