(ns member.routes
  (:require [iface.odin.routes :as iroutes]
            [re-frame.core :refer [reg-event-fx]]))


(def app-routes
  [""
   [["/accounts" [["" :accounts/list]
                  [["/" :account-id] :accounts/entry]]]

    ["/metrics" :metrics]

    ["/orders" [["" :orders]
                [["/" :order-id] :orders/entry]]]

    ["/kami" :kami]

    ["/profile" [["" :profile/membership]
                 ;; NOTE: Unnecessary because this is the default
                 ;; ["/membership" :profile/membership]
                 ["/contact" :profile/contact]

                 ["/payments"
                  [[""         :profile.payment/history]
                   ["/sources" :profile.payment/sources]]]

                 ["/settings"
                  [["/change-password" :profile.settings/change-password]]]]]

    ["/logout" :logout]

    [true :home]]])


(def path-for
  (partial iroutes/path-for app-routes))


(defmethod iroutes/dispatches :home [_]
  [[::home]])


(reg-event-fx
 ::home
 (fn [_ _]
   {:route (path-for :metrics)}))
