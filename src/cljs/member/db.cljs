(ns member.db
  (:require [member.profile.db :as profile]
            [member.services.db :as services]
            [iface.modules.loading :as loading]))


(defn bootstrap [account]
  (merge
   {:lang    :en
    :menu    {:showing false
              :items   [{:key  :services
                         :name "Premium Services"
                         :uri  "/services/book"}]}
    :account account
    :route   {:page      :home
              :path      [:home]
              :params    {}
              :requester account}}
   loading/db
   profile/default-value
   services/default-value))
