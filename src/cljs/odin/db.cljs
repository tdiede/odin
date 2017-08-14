(ns odin.db
  (:require [odin.account.db :as accounts]
            [odin.routes :as routes]))




(def ^:private menu-items
  [{:feature :home
    :uri     "/"}
   {:feature :profile
    :uri     "/profile"}
   ;; {:feature :people
   ;;  :uri     (routes/path-for :account/list)}
   ;; {:feature :communities
   ;;  :uri     "/communities"}
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
              :root    :home
              :params  {}}}
   accounts/default-value))
