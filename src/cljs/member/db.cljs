(ns member.db
  (:require [iface.odin.modules.loading :as loading]))

(defn bootstrap [account]
  (merge
   {:lang    :en
    :menu    {:showing false
              :items   [{:key  :metrics
                         :name "Metrics"
                         :uri  "/metrics"}
                        {:key  :accounts
                         :name "Accounts"
                         :uri  "/accounts"}
                        {:key  :orders
                         :name "Orders"
                         :uri  "/orders"}
                        {:key  :kami
                         :name "Kami"
                         :uri  "/kami"}]}
    :account account
    :route   {:page      :home
              :path      [:home]
              :params    {}
              :requester account}}
   loading/db))
