(ns odin.db
  (:require [odin.account.db :as accounts]
            [odin.components.modals :as modals]
            [odin.profile.payments.db :as payments]
            [odin.routes :as routes]
            [toolbelt.core :as tb]))




(def ^:private menu-items
  [{:feature :home
    :uri     "/"}
   {:feature :profile
    :uri     "/profile"}
   ;; {:feature :people
   ;;  :uri     (routes/path-for :account/list)}
   {:feature :communities
    :uri     "/communities"}
   ;; {:feature :orders
   ;;  :uri     "/orders"}
   ;; {:feature :services
   ;;  :uri     "/services"}
   ])


(def default-value
  (merge
   {:lang    :en
    :loading {:config true}
    :menu    {:showing false
              :items   menu-items}
    :route   {:current :home
              :path    [:home]
              :params  {}}}
   accounts/default-value
   payments/default-value
   modals/default-value))


(defn configure [config]
  (-> (assoc default-value :config config)
      (assoc-in [:route :requester] (:account config))))
