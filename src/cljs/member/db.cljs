(ns member.db
  (:require [member.profile.db :as profile]
            [iface.modules.loading :as loading]))


(defn bootstrap [account]
  (merge
   {:lang    :en
    :menu    {:showing false
              :items   []}
    :account account
    :route   {:page      :home
              :path      [:home]
              :params    {}
              :requester account}}
   loading/db
   profile/default-value))
