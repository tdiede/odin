(ns member.routes
  (:require [iface.utils.routes :as iroutes]
            [re-frame.core :refer [reg-event-fx]]))


(def app-routes
  [""
   [["/profile" [["" :profile/membership]

                 ;; NOTE: Unnecessary because this is the default
                 ;; ["/membership" :profile/membership]

                 ["/contact" :profile/contact]

                 ["/payments"
                  [[""         :profile.payment/history]
                   ["/sources" :profile.payment/sources]]]

                 ["/settings"
                  [["/change-password" :profile.settings/change-password]]]]]


    ["/services" [["/book" :services/book]
                  ["/active-orders" :services/active-orders]
                  ["/subscriptions" :services/subscriptions]
                  ["/history" :services/history]
                  ["/cart" :services/cart]]]

    ["/logout" :logout]

    [true :home]]])


(def path-for
  (partial iroutes/path-for app-routes))


(defmulti dispatches
  "Define additional events to dispatch when `route` is navigated to.

  Matches either the key identified by `:page`, or to the first key in
  `path` (which represents the 'root' route.)."
  (fn [route]
    (iroutes/route-dispatch dispatches route)))


(defmethod dispatches :default [route] [])


(defmethod dispatches :home [_]
  [[::home]])


(iroutes/install-events-handlers! dispatches)


(reg-event-fx
 ::home
 (fn [_ _]
   {:route (path-for :profile/membership)}))
