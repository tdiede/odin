(ns odin.db
  (:require [odin.accounts.db :as accounts]
            [odin.components.modals :as modals]
            [odin.global.db :as global]
            [odin.history.db :as history]
            [odin.kami.db :as kami]
            [odin.metrics.db :as metrics]
            [odin.orders.db :as orders]
            [odin.payments.db :as payments]
            [odin.payment-sources.db :as payment-sources]
            [odin.profile.db :as profile]
            [odin.properties.db :as properties]
            [odin.services.db :as services]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))


(def ^:private role->menu-items
  {:account.role/member
   [#_{:feature :home
     :uri     "/"}
    {:feature :services
     :uri     "/services"}]
   :account.role/admin
   [#_{:feature :home
       :uri     "/"}
    {:feature :metrics
     :uri     "/metrics"}
    {:feature :accounts
     :uri     "/accounts"}
    {:feature :orders
     :uri     "/orders"}
    {:feature :kami
     :uri     "/kami"}
    #_{:feature :communities
     :uri     "/communities"}]})


(defn- menu-items [config]
  (let [role (get-in config [:account :role])]
    (role->menu-items role)))


(defn bootstrap-db [config]
  (merge
   {:lang    :en
    :loading {:config true}
    :menu    {:showing false
              :items   (menu-items config)}
    :route   {:current :home
              :page    :home
              :path    [:home]
              :params  {}}
    :config  config}
   global/default-value
   accounts/default-value
   history/default-value
   payments/default-value
   payment-sources/default-value
   modals/default-value
   orders/default-value
   profile/default-value
   properties/default-value
   services/default-value
   kami/default-value
   metrics/default-value))


(defn configure [config]
  (-> (bootstrap-db config)
      (assoc-in [:route :requester] (:account config))))
