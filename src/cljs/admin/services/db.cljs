(ns admin.services.db
  (:require [admin.routes :as routes]
            [admin.services.orders.db :as orders-db]))



(def path ::path)

(def form-defaults {:name        ""
                    :description ""
                    :code        ""
                    :properties  []
                    :catalogs    []
                    :active      false
                    :type        :service
                    :price       0.0
                    :cost        0.0
                    :billed      :once
                    :fees        []
                    :rental      false
                    :fields      []})


(def form-validation-defaults
  {:name        true
   :description true
   :code        true})


(def default-value
  (merge {path {:from            ""
                :to              ""
                :service-id      nil
                :search-text     ""
                :is-editing      false
                :form            form-defaults
                :form-validation form-validation-defaults}}
         orders-db/default-value))
