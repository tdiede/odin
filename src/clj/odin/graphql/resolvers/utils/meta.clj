(ns odin.graphql.resolvers.utils.meta
  (:require [toolbelt.datomic :as td]
            [blueprints.models.source :as source]))


(defn- create*
  [db entity attr value]
  (let [[lm lmb] ((juxt td/last-modified-to source/tx-account)
                  db (:db/id entity) attr value)]
    {:attr             attr
     :value            value
     :last_modified    lm
     :last_modified_by lmb}))


(defn create
  [db entity avps]
  (map
   (fn [avp]
     (if-let [[a v override] (and (vector? avp) avp)]
       (if (some? override)
         (if-let [date (get entity override)]
           (let [meta {:attr a :value v}]
             (when-not (inst? date)
               (throw (ex-info "Invalid override key provided! Lookup produced non-date."
                               {:override override
                                :entity   entity
                                :non-date date})))
             (-> (assoc meta :last_modified date)
                 (assoc :last_modified_by (source/tx-account db (:db/id entity) override date))))
           (create* db entity a v))
         (create* db entity a v))
       (create* db entity avp (get entity avp))))
   avps))
