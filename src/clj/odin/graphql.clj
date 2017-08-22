(ns odin.graphql
  (:require [odin.graphql.resolvers :as resolvers]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [mount.core :refer [defstate]]
            [datomic.api :as d]))

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
    {:parse     (schema/as-conformer identity)
     :serialize (schema/as-conformer identity)}}})


(defstate schema
  :start (-> (io/resource "graphql/schema.edn")
             slurp
             edn/read-string
             (merge custom-scalars)
             (util/attach-resolvers resolvers/resolvers)
             schema/compile))


(comment
  (do
    (require '[com.walmartlabs.lacinia :refer [execute]])
    (require '[odin.datomic :refer [conn]])
    (require '[datomic.api :as d])
    (require '[venia.core :as venia]))

  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (execute schema
             (venia/graphql-query
              {:venia/queries
               [[:payment_sources {:account (:db/id account)}
                 [:id :type :status :name :last4 [:payments [:id :method :autopay [:source [:id]]]]]]] })
             nil
             {:db        (d/db conn)
              :conn      conn
              :stripe    (odin.config/stripe-secret-key odin.config/config)
              :requester (d/entity (d/db conn) [:account/email "member@test.com"])}))

  (let [account (d/entity (d/db conn) [:account/email "member@test.com"])]
    (->> (execute schema
                  (venia/graphql-query
                   {:venia/queries
                    [[:payments {:account (:db/id account)}
                      [:id :method :autopay :for [:source [:id :last4]]]]]})
                  nil
                  {:db        (d/db conn)
                   :conn      conn
                   :stripe    (odin.config/stripe-secret-key odin.config/config)
                   :requester (d/entity (d/db conn) [:account/email "member@test.com"])})
         :data
         :payments
         (map (partial into {}))))


  (execute schema
           ;; "query { accounts(role: member) { id, name, property { code } }}"
           (venia.core/graphql-query
            {:venia/queries
             [[:accounts {:role :member}
               [:id :name [:property [:code]] :email]]] })
           nil
           {:db   (d/db conn)
            :conn conn})


  )
