(ns odin.kami.db
  (:require [toolbelt.core :as tb]))


(def path ::path)


(def default-value
  {path {:query            ""
         :addresses        []
         :selected-address nil
         :report           {}}})


(defn query-params
  [db]
  (tb/assoc-when
   {}
   :q (:query db)
   :addr (:eas_baseid (:selected-address db))))
