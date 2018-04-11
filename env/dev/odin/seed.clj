(ns odin.seed
  (:require [blueprints.seed.accounts :as accounts]
            [blueprints.seed.orders :as orders]
            [blueprints.models.license :as license]
            [io.rkn.conformity :as cf]
            [datomic.api :as d]
            [toolbelt.core :as tb]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clojure.string :as string]
            [teller.property :as tproperty]
            [teller.payment :as tpayment]
            [teller.customer :as tcustomer]))


(defn- referrals []
  (let [sources ["craigslist" "word of mouth" "video" "starcity member" "instagram"]
        total   (inc (rand-int 100))]
    (mapv
     (fn [_]
       {:db/id           (d/tempid :db.part/starcity)
        :referral/source (rand-nth sources)
        :referral/from   :referral.from/tour})
     (range total))))


(defn- rand-unit [property]
  (-> property :property/units vec rand-nth :db/id))

(defn rand-text []
  (->> ["Fusce suscipit, wisi nec facilisis facilisis, est dui fermentum leo, quis tempor ligula erat quis odio." "Sed bibendum." "Donec at pede." "Fusce suscipit, wisi nec facilisis facilisis, est dui fermentum leo, quis tempor ligula erat quis odio." "Pellentesque condimentum, magna ut suscipit hendrerit, ipsum augue ornare nulla, non luctus diam neque sit amet urna." "Fusce commodo." "Nullam tempus." "Etiam vel tortor sodales tellus ultricies commodo." "Donec at pede." "Nullam rutrum." "Nullam eu ante vel est convallis dignissim." "Aenean in sem ac leo mollis blandit." "Cras placerat accumsan nulla." "Integer placerat tristique nisl." "Phasellus purus." "Nullam eu ante vel est convallis dignissim." "Nullam tristique diam non turpis." "Aliquam erat volutpat." "In id erat non orci commodo lobortis." "Proin quam nisl, tincidunt et, mattis eget, convallis nec, purus." "Fusce sagittis, libero non molestie mollis, magna orci ultrices dolor, at vulputate neque nulla lacinia eros." "Phasellus neque orci, porta a, aliquet quis, semper a, massa." "Lorem ipsum dolor sit amet, consectetuer adipiscing elit." "Donec hendrerit tempor tellus." "Pellentesque condimentum, magna ut suscipit hendrerit, ipsum augue ornare nulla, non luctus diam neque sit amet urna." "Proin quam nisl, tincidunt et, mattis eget, convallis nec, purus." "Cras placerat accumsan nulla." "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus." "Vivamus id enim." "Mauris mollis tincidunt felis." "Integer placerat tristique nisl." "Nunc eleifend leo vitae magna." "Phasellus neque orci, porta a, aliquet quis, semper a, massa." "Aliquam posuere." "Nunc rutrum turpis sed pede." "Pellentesque dapibus suscipit ligula." "Curabitur vulputate vestibulum lorem." "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus." "Donec pretium posuere tellus." "Fusce sagittis, libero non molestie mollis, magna orci ultrices dolor, at vulputate neque nulla lacinia eros."]
       (take (rand-int 41))
       (apply str)))


(defn fill-application [db app]
  (let [pet (when (= (rand-int 2) 0)
              {:pet/type         :dog
               :pet/breed        "pitbull"
               :pet/weight       60
               :pet/sterile      false
               :pet/vaccines     false
               :pet/bitten       true
               :pet/demeanor     "eats babies"
               :pet/daytime-care "loves being around children"})]
    (merge
     app
     {:application/communities (take (inc (rand-int 2)) [[:property/internal-name "52gilbert"]
                                                         [:property/internal-name "2072mission"]])
      :application/license     (:db/id (license/by-term db (rand-nth [3 6 12])))
      :application/move-in     (c/to-date (t/plus (t/now) (t/weeks 2)))
      :application/has-pet     (some? pet)
      :application/fitness     {:fitness/experience   (rand-text)
                                :fitness/skills       (rand-text)
                                :fitness/free-time    (rand-text)
                                :fitness/conflicts    (rand-text)
                                :fitness/dealbreakers (rand-text)
                                :fitness/interested   (rand-text)}
      :application/address     {:address/lines       "1020 Kearny St."
                                :address/locality    "San Francisco"
                                :address/region      "CA"
                                :address/postal-code "94133"
                                :address/country     "US"}
      :application/status      (rand-nth [:application.status/in-progress
                                          :application.status/submitted])}
     (when (some? pet) {:application/pet pet}))))


(defn- applicant [db]
  (let [[acct app] (accounts/applicant)]
    [acct (fill-application db app)]))


(defn- accounts [db]
  (let [license    (license/by-term db 3)
        property   (d/entity db [:property/internal-name "2072mission"])
        distinct   (fn [coll] (tb/distinct-by (comp :account/email #(tb/find-by :account/email %)) coll))
        members    (->> (repeatedly #(accounts/member (rand-unit property) (:db/id license)))
                        (take 13)
                        distinct
                        (apply concat))
        applicants (->> (repeatedly #(applicant db)) (take 15) distinct)]
    (apply concat
           (accounts/member [:unit/name "52gilbert-1"] (:db/id license) :email "member@test.com")
           (accounts/admin :email "admin@test.com")
           members
           applicants)))


(defn- rand-date []
  (c/to-date (t/date-time 2017 (inc (rand-int 12)) (inc (rand-int 28)))))


(defn- seed-properties [teller]
  (let [fees (tproperty/construct-fees (tproperty/fee 5))]
    (tproperty/create! teller "52gilbert" "52 Gilbert" "jesse@starcity.com"
                       {:fees      fees
                        :deposit   "acct_1C5LJXEd7myLyyjs"
                        :ops       "acct_1C3TmPHnEDeEkGIS"
                        :community [:property/code "52gilbert"]})
    (tproperty/create! teller "2072mission" "2072 Mission" "jesse@starcity.com"
                       {:fees      fees
                        :deposit   "acct_1C3S9tD1iZkoyuLX"
                        :ops       "acct_1C3TmMEBSLaHdiO2"
                        :community [:property/code "2072mission"]})))


(def mock-visa
  {:object    "card"
   :exp_month 12
   :exp_year  23
   :number    "4242424242424242"})


(defn- seed-payments [teller]
  (let [customer (tcustomer/create! teller "member@test.com"
                                    {:account  [:account/email "member@test.com"]
                                     :source   mock-visa
                                     :property (tproperty/by-id teller "52gilbert")})]
    (tpayment/create! customer 25.0 :payment.type/application-fee)
    (tpayment/create! customer 500.0 :payment.type/deposit)
    (tpayment/create! customer 2000.0 :payment.type/rent {:period [#inst "2018-05-31"
                                                                   #inst "2018-06-30"]})))


(defn seed-teller [teller]
  (seed-properties teller)
  (seed-payments teller))


(defn seed [conn]
  (let [db          (d/db conn)
        license     (license/by-term db 3)
        accounts-tx (accounts db)
        member-ids  (->> accounts-tx
                         (filter #(and (:account/email %) (= :account.role/member (:account/role %))))
                         (map (fn [m] [:account/email (:account/email m)])))]
    (->> {:seed/accounts  {:txes [accounts-tx]}
          :seed/referrals {:txes [(referrals)]}
          :seed/orders    {:txes     [(orders/gen-orders db member-ids)]
                           :requires [:seed/accounts]}
          :seed/onboard   {:txes     [(accounts/onboard [:account/email "admin@test.com"] [:unit/name "52gilbert-1"] (:db/id license)
                                                        :email "onboard@test.com")]
                           :requires [:seed/accounts]}}
         (cf/ensure-conforms conn))))
