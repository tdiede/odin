(ns odin.graphql.resolvers.utils
  (:require [clojure.spec :as s]
            [ribbon.core :as ribbon]
            [toolbelt.predicates :as p]))


(s/def ::conn p/conn?)
(s/def ::requester p/entityd?)
(s/def ::stripe ribbon/conn?)
(s/def ::socrata string?)


(s/def ::ctx
  (s/keys :req-un [::stripe ::requester ::conn ::socrata]))


(defn context? [x]
  (s/valid? ::ctx x))


(defn context
  "Construct a new context map."
  [conn requester stripe socrata]
  {:conn      conn
   :requester requester
   :stripe    stripe
   :socrata   socrata})

(s/fdef context
        :args (s/cat :conn ::conn
                     :requester ::requester
                     :stripe ::stripe
                     :socrata ::socrata)
        :ret ::ctx)
