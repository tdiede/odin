(ns odin.graphql
  (:require [clj-time.coerce :as c]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [datomic.api :as d]
            [mount.core :refer [defstate]]
            [odin.graphql.resolvers :as resolvers]))

(defn- parse-keyword [s]
  (let [[ns' n'] (string/split s #"/")]
    (keyword ns' n')))


(def custom-scalars
  {:scalars
   {:Long
    {:parse     (schema/as-conformer #(Long. %))
     :serialize (schema/as-conformer #(Long. %))}

    :Keyword
    {:parse     (schema/as-conformer
                 #(format "%s/%s" (namespace %) (name %)))
     :serialize (schema/as-conformer identity)}

    :Instant
    {:parse     (schema/as-conformer (comp c/to-date c/from-string))
     :serialize (schema/as-conformer identity)}

    :Any
    {:parse     (schema/as-conformer identity)
     :serialize (schema/as-conformer identity)}}})


(defn- read-resource [path]
  (-> (io/resource path)
      slurp
      edn/read-string))


(defstate schema
  :start (-> (read-resource "graphql/schema.edn")
             (merge custom-scalars)
             (util/attach-resolvers (resolvers/resolvers))
             schema/compile))


(comment
  (do
    (require '[com.walmartlabs.lacinia :refer [execute]])
    (require '[odin.datomic :refer [conn]])
    (require '[datomic.api :as d])
    (require '[venia.core :as venia])

    (defn pretty [result]
      (letfn [(-prettify [m]
                (reduce
                 (fn [acc [k v]]
                   (assoc acc k
                          (cond
                            (instance? flatland.ordered.map.OrderedMap v) (into {} (-prettify v))
                            (sequential? v)                               (map -prettify v)
                            :otherwise                                    v)))
                 {}
                 m))]
        (update result :data -prettify)))

    (def token "btok_1AwfkJIvRccmW9nOZMX9bgQO")
    (def source "ba_1AwfkJIvRccmW9nOoEbGvWZn")

    (def ctx {:conn      conn
              :stripe    (odin.config/stripe-secret-key odin.config/config)
              :requester (d/entity (d/db conn) [:account/email "member@test.com"])})

    )


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (pretty
     (execute schema
              (venia/graphql-query
               {:venia/queries
                [[:payments {:account (:db/id account)}
                  [:id :type [:order [:id]] [:property [:name]]]]]})
              nil
              ctx)))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:update_account {:id   (:db/id account)
                                        :data {:emergency_contact {:first_name "Dad"
                                                                   :last_name  "Carter"
                                                                   :phone      "asl;dkfjas"}}}
                       [:id :name :phone [:emergency_contact [:name :phone]]]]]}))
              nil
              {:conn      conn
               :stripe    (odin.config/stripe-secret-key odin.config/config)
               :requester (d/entity (d/db conn) [:account/email "member@test.com"])})))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (pretty
     (execute schema
              (venia/graphql-query
               {:venia/queries
                [[:payments {:account (:db/id account)}
                  [:id :autopay :for :description]]] })
              nil
              {:conn      conn
               :stripe    (odin.config/stripe-secret-key odin.config/config)
               :requester (d/entity (d/db conn) [:account/email "member@test.com"])})))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx     {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester account}]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:set_default_source {:id "card_1AV6tzIvRccmW9nOhQsWMTuv"}
                       [:id :type :default]]]}))
              nil
              ctx)))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx     {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester account}]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:add_payment_source {:token token}
                       [:id :type]]]}))
              nil
              ctx)))


  (let [account  (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx      {:conn      conn
                  :stripe    (odin.config/stripe-secret-key odin.config/config)
                  :requester account}
        deposits [32 45]]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:verify_bank_source {:deposits deposits :id source}
                       [:id :type :status]]]}))
              nil
              ctx)))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx     {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester account}]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:set_autopay_source {:id source} [:id :type :status :autopay]]]}))
              nil
              ctx)))


  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx     {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester account}]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:unset_autopay_source {:id source} [:id :type :status :autopay]]]}))
              nil
              ctx)))


  ;; Cannot delete, as it's being used for autopay.
  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])
        ctx     {:conn      conn
                 :stripe    (odin.config/stripe-secret-key odin.config/config)
                 :requester account}]
    (pretty
     (execute schema
              (str "mutation"
                   (venia/graphql-query
                    {:venia/queries
                     [[:delete_payment_source {:id source} [:id :autopay]]]}))
              nil
              ctx)))



  )
