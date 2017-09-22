(ns odin.seed
  (:require [blueprints.models.application :as app]
            [blueprints.models.approval :as approval]
            [blueprints.models.license :as license]
            [blueprints.models.member-license :as member-license]
            [blueprints.models.onboard :as onboard]
            [blueprints.models.order :as order]
            [blueprints.models.payment :as payment]
            [blueprints.models.promote :as promote]
            [blueprints.models.property :as property]
            [blueprints.models.security-deposit :as deposit]
            [blueprints.models.service :as service]
            [blueprints.models.unit :as unit]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [datomic.api :as d]
            [io.rkn.conformity :as cf]
            [mount.core :refer [defstate]]
            [odin.datomic :refer [conn]]
            [taoensso.timbre :as timbre]
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

(defn- rent-payments-tx [conn]
  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        license (member-license/active (d/db conn) account)]
    [(member-license/add-rent-payments
      license
      (payment/create 2000.0 account
                      :for :payment.for/rent
                      :method :payment.method/stripe-invoice
                      :invoice-id "in_1Am9sNJDow24Tc1aFeswkDT1"
                      :charge-id "py_1AmAozJDow24Tc1aKfpUUIe9"
                      :source-id "ba_19Z7BcJDow24Tc1aZBrHmWB5"
                      :pstart (date 2017 7 1)
                      :due (date 2017 7 1)
                      :pend (date 2017 8 1)
                      :paid-on (date 2017 7 3))
      (payment/create 2000.0 account
                      :for :payment.for/rent
                      :method :payment.method/stripe-invoice
                      :invoice-id "in_1Aav6WJDow24Tc1aVFckJH4K"
                      :charge-id "py_1Aaw2eJDow24Tc1axiqIh6bK"
                      :source-id "ba_19Z7BcJDow24Tc1aZBrHmWB5"
                      :pstart (date 2017 6 1)
                      :due (date 2017 6 1)
                      :pend (date 2017 7 1)
                      :paid-on (date 2017 6 2)))
     {:db/id                          (:db/id license)
      :member-license/subscription-id "sub_9ssx9DacEP1g4Y"}]))


;; =============================================================================
;; Rent Payments


(defn- storage-bins [db account]
  (let [service (service/by-code db "storage,bin,small")]
    (-> (order/create account service
                      {:quantity 4.0
                       :status   :order.status/charged})
        (assoc :stripe/subs-id "sub_AqocuGJcK71Uf2"
               :order/payments (payment/create 48.0 account
                                               :for :payment.for/order
                                               :paid-on (date 2017 6 15)
                                               :invoice-id "in_1ArE44IvRccmW9nOu8hqIlti"
                                               :charge-id "ch_1ArF0rIvRccmW9nOZMFFadb7"
                                               :source-id "card_1AV6tzIvRccmW9nOhQsWMTuv"
                                               :method :payment.method/stripe-invoice)))))


(defn- customize-furniture [db account]
  (let [service (service/by-code db "customize,furniture,quote")]
    (-> (order/create account service
                      {:price  75.0
                       :desc   "We customized some furniture and shit."
                       :status :order.status/charged})
        (assoc :order/payments (payment/create 75.0 account
                                               :for :payment.for/order
                                               :paid-on (date 2017 6 19)
                                               :charge-id "ch_1AV7PpIvRccmW9nOgAgD793D"
                                               :source-id "card_1AV6tzIvRccmW9nOhQsWMTuv"
                                               :method :payment.method/stripe-charge)))))


(defn- kitchenette [db account]
  (let [service (service/by-code db "kitchenette,coffee/tea,bundle")]
    (-> (order/create account service
                      {:variant (d/q '[:find ?e .
                                       :where
                                       [?e :svc-variant/name "Chemex"]]
                                     db)
                       :status  :order.status/charged})
        (assoc :order/payments (payment/create 75.0 account
                                               :for :payment.for/order
                                               :paid-on (date 2017 7 5)
                                               :charge-id "ch_1AV7OkIvRccmW9nOtgRbZ1WK"
                                               :source-id "card_1AV6tzIvRccmW9nOhQsWMTuv"
                                               :method :payment.method/stripe-charge)))))


;; Create orders for three services backed by actual Stripe data.
(defn orders-tx [db]
  (let [account (d/entity db [:account/email "member@test.com"])]
    ((juxt storage-bins customize-furniture kitchenette) db account)))


;; =============================================================================
;; Security Deposit


(defn- next-week []
  (c/to-date (t/plus (t/now) (t/weeks 1))))


(defn- next-month []
  (c/to-date (t/plus (t/now) (t/months 1))))


(defn deposit-payments-tx [db]
  (let [account (d/entity db [:account/email "member@test.com"])
        deposit (deposit/by-account account)
        payment (payment/create 500.0 account
                                :status :payment.status/paid
                                :charge-id "py_1AjwerIvRccmW9nOMZ78NJJN"
                                :source-id "ba_1AjwecIvRccmW9nO175kwr0e"
                                :paid-on (date 2017 6 1)
                                :for :payment.for/deposit
                                :due (next-week))]
    [(deposit/add-payment deposit payment) payment]))


(defn stripe-customers-tx []
  ;; []
  [{:db/id                              (d/tempid :db.part/starcity)
    :stripe-customer/account            [:account/email "member@test.com"]
    :stripe-customer/customer-id        "cus_B68wMlijSv2U5p"
    :stripe-customer/bank-account-token "ba_1AjwecIvRccmW9nO175kwr0e"}
   ;; {:db/id                              (d/tempid :db.part/starcity)
   ;;  :stripe-customer/account            [:account/email "member@test.com"]
   ;;  :stripe-customer/customer-id        "cus_9ssxgKtsJ02bVo"
   ;;  :stripe-customer/bank-account-token "ba_19Z7BcJDow24Tc1aZBrHmWB5"
   ;;  ;; :stripe-customer/bank-account-token "ba_1AV6tfIvRccmW9nOfjsLP6DZ"
   ;;  :stripe-customer/managed            [:property/internal-name "52gilbert"]}
   ])


(defn avatar-tx []
  [{:db/id       (d/tempid :db.part/starcity)
    :avatar/name :system
    :avatar/url  "/assets/img/starcity-logo-black.png"}])


(defn referrals-tx []
  (let [sources ["craigslist" "word of mouth" "video" "starcity member" "instagram"]
        total   (inc (rand-int 100))]
    (mapv
     (fn [_]
       {:db/id           (d/tempid :db.part/starcity)
        :referral/source (rand-nth sources)
        :referral/from   :referral.from/tour})
     (range total))))

(defn properties-tx []
  [{:db/id                    [:property/internal-name "52gilbert"]
    :property/cover-image-url "/assets/images/52gilbert.jpg"}
   {:db/id                    [:property/internal-name "2072mission"]
    :property/cover-image-url "/assets/images/2072mission.jpg"}])


;; =============================================================================
;; API
;; =============================================================================

(defn- seed
  "Seed the database with sample data."
  [conn]
  (cf/ensure-conforms
   conn
   {:seed/accounts         {:txes [(accounts-tx)]}
    :seed/applications     {:txes     [(applications-tx conn)]
                            :requires [:seed/accounts]}
    :seed/stripe-customers {:txes     [(stripe-customers-tx)]
                            :requires [:seed/accounts]}
    :seed/avatar           {:txes [(avatar-tx)]}
    :seed/referrals        {:txes [(referrals-tx)]}
    :seed/properties       {:txes [(properties-tx)]}})
  ;; NOTE: These need to happen in separate transactions.
  (cf/ensure-conforms
   conn
   {:seed/approval {:txes [(approval-tx conn)]}})
  (cf/ensure-conforms
   conn
   {:seed/membership {:txes [(member-licenses-tx conn)
                             ;; (rent-payments-tx conn)
                             (deposit-payments-tx (d/db conn))]}})
  #_(cf/ensure-conforms
   conn
   {:seed/orders {:txes [(orders-tx (d/db conn))]}}))


(defstate seeder
  :start (do
           (timbre/debug "seeding dev database...")
           (seed conn)))
