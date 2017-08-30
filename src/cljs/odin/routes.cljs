(ns odin.routes
  (:require [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [re-frame.core :refer [dispatch reg-fx]]
            [toolbelt.core :as tb]
            [cemerick.url :as c]
            [clojure.walk :as walk]
            [clojure.string :as string]))


(def app-routes
  [""
   [

    ;; ["/people" [["" :account/list]
    ;;               [["/" :account-id]
    ;;                [["" :account/entry]]]]]

    ["/profile" [["" :profile/membership]
                 ;; NOTE: Unnecessary because this is the default
                 ;; ["/membership" :profile/membership]
                 ["/contact" :profile/contact]
                 ["/payments"
                  [[""         :profile.payment/history]
                   ["/sources" :profile.payment/sources]]]
                   ;;["/sources" [["" :profile.payment/sources]
                                ;;[["/" :source-id] :profile.payment.sources/entry]]]]]
                 ["/settings"
                  [["/change-password" :profile.settings/change-password]]]]]

    ;; ["/communities" [["" :properties]]]

    ;; ["/services" [["" :services]]]

    [true :home]

    ]])


;; :profile.payment/sources => [:profile :payment :sources]


(defmulti dispatches (fn [route] (:page route)))


(defmethod dispatches :default [route]
  [])


(def ^:private dummy-base
  "http://foo.com")

(defn baseify-uri
  "Adds a fake protocol and domain to URI string,
  for Cemerick's url function to interpret it properly."
  [uri]
  (if (nil? (re-find #"http" uri))
    (str dummy-base uri)
    uri))

(defn unbaseify-uri
  "Remove the thing."
  [uri]
  (string/replace uri (re-pattern dummy-base) ""))

(defn uri
  [uri]
  (c/url (baseify-uri uri)))


(defn parse-query-params
  "Parses query parameters from a URL and yields them as a Clojure map."
  [path]
  (walk/keywordize-keys (:query (uri path))))


(defn hook-browser-navigation!
  "Wire up the bidi routes to the browser HTML5 navigation."
  [routes]
  (accountant/configure-navigation!
   {:nav-handler  (fn [path]
                    (let [match  (bidi/match-route app-routes path)
                          page   (:handler match)
                          params (merge (parse-query-params path)
                                        (:route-params match))]
                      (dispatch [:route/change page params])))
    :path-exists? (fn [path]
                    (boolean (bidi/match-route app-routes path)))})
  (accountant/dispatch-current!))


;;(def path-for
;;  "Produce the path (URI) for `key`."
;;  (partial bidi/path-for app-routes))


;; (routes/path-for :profile/membership)
;; (routes/path-for :profile/membership :account-id 1232455)
;; (routes/path-for :profile/membership :account-id 1234566 :query-params {:something "foo"})

;; /profile/membership/12345666
;; /profile/membership/12345666?something=foo

(defn append-query-params
  [path params]
  (if-let [query-params (:query-params params)]
    (-> (uri path)
        (assoc :query query-params)
        str)
    path))


(defn get-route-params
  [params]
  (dissoc params :query-params))


(defn path-for
  "Produce the path (URI) for `key`."
  ([route]
   (path-for route {}))
  ([route & {:as params}]
   (-> (bidi/path-for app-routes route (get-route-params params))
       (append-query-params params)
       unbaseify-uri)))

  ;;(partial bidi/path-for app-routes))


(reg-fx
 :route
 (fn [new-route]
   (if (vector? new-route)
     (let [[route query] new-route]
       (accountant/navigate! route query))
     (accountant/navigate! new-route))))
