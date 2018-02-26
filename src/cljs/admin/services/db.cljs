(ns admin.services.db
  (:require [admin.routes :as routes]
            [admin.services.orders.db :as orders-db]))



(def path ::path)


(def default-value
  (merge {path {:from        ""
                :to          ""
                :service-id  nil
                :search-text ""}}
         orders-db/default-value))
