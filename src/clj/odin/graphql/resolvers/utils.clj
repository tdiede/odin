(ns odin.graphql.resolvers.utils
  (:require [clojure.spec :as s]
            [ribbon.core :as ribbon]
            [toolbelt.predicates :as p]))


(s/def ::conn p/conn?)
(s/def ::requester p/entityd?)
(s/def ::stripe ribbon/conn?)


(s/def ::ctx
  (s/keys :req-un [::stripe ::requester ::conn]))


(defn context? [x]
  (s/valid? ::ctx x))


(defn context
  "Construct a new context map."
  [conn requester stripe]
  {:conn      conn
   :requester requester
   :stripe    stripe})

(s/fdef context
        :args (s/cat :conn p/conn?
                     :requester p/entityd?
                     :stripe ribbon/conn?)
        :ret ::ctx)
