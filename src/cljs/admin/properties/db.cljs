(ns admin.properties.db
  (:require [toolbelt.core :as tb]
            [iface.utils.norms :as norms]))


(def path ::properties)


(def default-value
  {path {:unit-rates {}}})


(defn unit-rates [unit]
  (let [urates (:rates unit)
        prates (-> unit :property :rates)]
    (map
     (fn [{:keys [term rate] :as prate}]
       (if-let [urate (tb/find-by (comp #{term} :term) urates)]
        urate
        prate))
    prates)))


(defn unit [db property-id unit-id]
  (let [units (:units (norms/get-norm db :properties/norms property-id))]
    (tb/find-by (comp #{unit-id} :id) units)))
