(ns odin.properties.subs
  (:require [odin.properties.db :as db]
            [odin.utils.norms :as norms]
            [re-frame.core :refer [reg-sub]]
            [toolbelt.core :as tb]))


(reg-sub
 db/path
 (fn [db _]
   (db/path db)))


(reg-sub
 :property
 :<- [db/path]
 (fn [db [_ property-id]]
   (norms/get-norm db :properties/norms property-id)))


(reg-sub
 :property/units
 :<- [db/path]
 (fn [db [_ property-id]]
   (->> (norms/get-norm db :properties/norms property-id)
        :units
        (sort-by :number))))
