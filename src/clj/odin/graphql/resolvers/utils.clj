(ns odin.graphql.resolvers.utils
  (:require [clojure.spec.alpha :as s]
            [ribbon.core :as ribbon]
            [toolbelt.core :as tb]
            [toolbelt.datomic :as td]))


(s/def ::conn td/conn?)
(s/def ::requester td/entityd?)
(s/def ::stripe ribbon/conn?)
(s/def ::config map?)


(s/def ::ctx
  (s/keys :req-un [::stripe ::requester ::conn ::config]))


(defn context? [x]
  (s/valid? ::ctx x))


(defn context
  "Construct a new context map."
  [conn requester stripe config]
  {:conn      conn
   :requester requester
   :stripe    stripe
   :config    config})

(s/fdef context
        :args (s/cat :conn ::conn
                     :requester ::requester
                     :stripe ::stripe
                     :config ::config)
        :ret ::ctx)


(defn error-message [t]
  (or (:message (ex-data t)) (.getMessage t) "Unknown error!"))

(s/fdef error-message
        :args (s/cat :throwable tb/throwable?)
        :ret string?)
