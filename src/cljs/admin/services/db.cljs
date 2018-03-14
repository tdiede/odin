(ns admin.services.db
  (:require [admin.routes :as routes]
            [admin.services.orders.db :as orders-db]))



(def path ::path)


(def default-value
  (merge {path {:from        ""
                :to          ""
                :service-id  nil
                :search-text ""
                :form        {:name        ""
                              :description ""
                              :code        ""
                              :properties  []
                              :catalogs    []
                              :price       0.0
                              :cost        0.0
                              :rental      false
                              :fields      []}}}
         orders-db/default-value))
