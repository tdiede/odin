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
    (require '[datomic.api :as d]))

  (execute schema
           ;; "query { accounts(role: member) { id, name, property { code } }}"
           (venia.core/graphql-query
            {:venia/queries
             [[:accounts {:role :member}
               [:id :name [:property [:code]] :email]]] })
           nil
           {:db   (d/db conn)
            :conn conn})


  ;; (venia.core/graphql-query
  ;;  {:venia/queries
  ;;   [[:set_phone {:id 285873023223058 :phone "2345678911"} [:id :name :email :role :phone]]]})





  )
