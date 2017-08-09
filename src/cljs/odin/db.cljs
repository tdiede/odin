(ns odin.db
  (:require [odin.account.db :as accounts]))


(def menu-items
  [{:menu/key :home
    :menu/uri "/"}
   {:menu/key :accounts}
   {:menu/key :properties}
   {:menu/key :services}
   {:menu/key         :log-out
    :menu/uri         "/logout"
    :menu/text        "Log Out"
    :menu.ui/excluded #{:side}}])


(def default-value
  (merge
   {:lang     :en
    :loading {:config true}
    :menu    {:showing false
              :items   menu-items}
    :route   {:current :home
              :root    :home
              :params  {}}}
   accounts/default-value))


(comment

  {:role :member
   :features
   {:home        {}
    :people      {}
    :communities {}
    :orders      {}
    :account     {}
    ;; admin only
    :services    {}}})
