(ns onboarding.routes
  (:require [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [onboarding.db :as db]
            [re-frame.core :refer [dispatch reg-fx]]))

(def app-routes
  ["/onboarding"
   (conj (db/menu->routes db/menu) [true :overview/start])])

(def path-for (partial bidi/path-for app-routes))

(defn hook-browser-navigation! []
  (accountant/configure-navigation!
   {:nav-handler  (fn [path]
                    (let [match  (bidi/match-route app-routes path)
                          page   (:handler match)
                          params (:route-params match)]
                      (dispatch [:app/route page params])))
    :path-exists? (fn [path]
                    (boolean (bidi/match-route app-routes path)))})
  (accountant/dispatch-current!))

(reg-fx
 :route
 (fn [new-route]
   (if (vector? new-route)
     (let [[route query] new-route]
       (accountant/navigate! route query))
     (accountant/navigate! new-route))))
