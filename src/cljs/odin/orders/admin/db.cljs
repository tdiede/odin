(ns odin.orders.admin.db
  (:require [cljs.spec.gen.alpha :as gen]
            [cljs.spec.alpha :as s]
            clojure.test.check))



(s/def ::id pos-int?)
(s/def ::name #{"Ela Garcia"
                "Derryl Carter"
                "Jon Dishotsky"
                "Mo Sakrani"
                "Jesse Suarez"
                "Esteve Almirall"
                "Josh Lehman"
                "Meg Bell"})
(s/def ::account
  (s/keys :req-un [::id ::name]))

(s/def ::price (s/and float? pos? #(> 500.0 %) #(> % 5.0)))
(s/def ::quantity (s/and float? pos?))
(s/def ::created inst?)
(s/def ::desc string?)
(s/def ::status #{:pending :canceled :charged :placed})
(s/def ::billed_on inst?)
(s/def :service/name
  #{"Dog Walking, one-off"
    "Box Fan"
    "Apple TV"
    "Room Cleaning, one-off"
    "Room Cleaning, Weekly"
    "Dry Cleaning, Weekly"})
(s/def ::property #{"West Soma" "The Mission"})
(s/def ::billed #{:once :monthly})
(s/def ::service
  (s/keys :req-un [::id :service/name ::billed]))
(s/def ::order
  (s/keys :req-un [::account ::price ::created ::status ::service ::property]
          :opt-un [::quantity ::billed_on ::desc ]))


(comment
  {:account   {:id   12356
               :name "Ela Garcia"}
   :price     25.0
   :quantity  2.0
   :desc      "Dog walking on 9/17"
   :created   #inst "2017-09-08"
   :status    :order.status/pending
   :billed_on nil
   :property  "West Soma"
   :service   {:id     8591040
               :name   "Dog Walking, one-off"
               :billed :service.billed/once}}

  {:account  {:id   12356
              :name "Ela Garcia"}
   :amount   25.0
   :for      :payment.for/order
   :created  #inst "2017-09-08"
   :status   :paid
   :paid_on  nil
   :property "West Soma"
   :service  {:id     8591040
              :name   "Dog Walking, one-off"
              :billed :service.billed/once}}


  )


(def ^:private sample-orders
  '({:account {:id 1, :name "Meg Bell"},
    :price 5.458984375,
    :created #inst "1970-01-01T00:00:00.000-00:00",
    :status :pending,
    :service {:id 1, :name "Room Cleaning, Weekly", :billed :once},
    :property "West Soma",
    :quantity 2,
    :desc ""}
   {:account {:id 1, :name "Esteve Almirall"},
    :price 6,
    :created #inst "1969-12-31T23:59:59.999-00:00",
    :status :canceled,
    :service {:id 1, :name "Box Fan", :billed :once},
    :property "West Soma",
    :quantity 1}
   {:service {:id 1, :name "Room Cleaning, one-off", :billed :once},
    :desc "9",
    :property "The Mission",
    :created #inst "1969-12-31T23:59:59.999-00:00",
    :account {:id 2, :name "Jesse Suarez"},
    :status :pending,
    :billed_on #inst "1969-12-31T23:59:59.999-00:00",
    :quantity 3.5,
    :price 6.875}
   {:service {:id 1, :name "Dry Cleaning, Weekly", :billed :once},
    :desc "",
    :property "The Mission",
    :created #inst "1970-01-01T00:00:00.001-00:00",
    :account {:id 1, :name "Ela Garcia"},
    :status :placed,
    :billed_on #inst "1970-01-01T00:00:00.001-00:00",
    :quantity 0.875,
    :price 5.25}
   {:account {:id 1, :name "Meg Bell"},
    :price 28.9375,
    :created #inst "1969-12-31T23:59:59.992-00:00",
    :status :canceled,
    :service {:id 8, :name "Dog Walking, one-off", :billed :once},
    :property "The Mission"}
   {:account {:id 3, :name "Jon Dishotsky"},
    :price 22.59375,
    :created #inst "1970-01-01T00:00:00.001-00:00",
    :status :placed,
    :service {:id 4, :name "Room Cleaning, one-off", :billed :once},
    :property "West Soma"}
   {:account {:id 3, :name "Meg Bell"},
    :price 24,
    :created #inst "1969-12-31T23:59:59.999-00:00",
    :status :canceled,
    :service {:id 4, :name "Dog Walking, one-off", :billed :monthly},
    :property "The Mission"}
   {:account {:id 2, :name "Ela Garcia"},
    :price 365.2601833343506,
    :created #inst "1969-12-31T23:59:59.990-00:00",
    :status :placed,
    :service {:id 6, :name "Room Cleaning, Weekly", :billed :once},
    :property "West Soma",
    :billed_on #inst "1970-01-01T00:00:00.000-00:00",
    :desc "y"}
   {:service {:id 3, :name "Room Cleaning, one-off", :billed :once},
    :desc "v42xy7OV",
    :property "The Mission",
    :created #inst "1970-01-01T00:00:00.027-00:00",
    :account {:id 2, :name "Derryl Carter"},
    :status :pending,
    :billed_on #inst "1969-12-31T23:59:59.999-00:00",
    :quantity 1.7265625,
    :price 5.5}
   {:account {:id 30, :name "Jesse Suarez"},
    :price 52,
    :created #inst "1970-01-01T00:00:00.000-00:00",
    :status :placed,
    :service {:id 112, :name "Box Fan", :billed :once},
    :property "West Soma"}))


(def path ::orders)


(def default-value
  {path {:chart {:orders (vec sample-orders)
                 :params {:chart-type "community"
                          :from       (.startOf (js/moment.) "month")
                          :to         (.endOf (js/moment.) "month")}}}})
