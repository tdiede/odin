(ns iface.utils.routes
  (:require [accountant.core :as accountant]
            [bidi.bidi :as bidi]
            [re-frame.core :as rf]
            [cemerick.url :as c]
            [clojure.walk :as walk]
            [clojure.string :as string]))


;; ==============================================================================
;; internal =====================================================================
;; ==============================================================================


(def ^:private dummy-base
  "http://foo.com")


(defn coerce-uri
  "Adds a fake protocol and domain to URI string,
  for Cemerick's url function to interpret it properly."
  [uri]
  (if (nil? (re-find #"http" uri))
    (str dummy-base uri)
    uri))


(defn parse-uri
  "Remove the fake protocol / domain from URI. See above."
  [uri]
  (string/replace uri (re-pattern dummy-base) ""))


(defn uri
  [uri]
  (c/url (coerce-uri uri)))


(defn parse-query-params
  "Parses query parameters from a URL and yields them as a Clojure map."
  [path]
  (walk/keywordize-keys (:query (uri path))))


(defn append-query-params
  [path params]
  (if-let [query-params (:query-params params)]
    (-> (uri path)
        (assoc :query query-params)
        str)
    path))


(defn- get-route-params
  [params]
  (dissoc params :query-params))


;; ==============================================================================
;; api ==========================================================================
;; ==============================================================================


(defn path-for
  "Produce the path (URI) for `route` within `routes`."
  ([routes route]
   (path-for routes route {}))
  ([routes route & {:as params}]
   (-> (apply bidi/path-for routes route (apply concat (get-route-params params)))
       (append-query-params params)
       parse-uri)))


(defn hook-browser-navigation!
  "Wire up the bidi routes to the browser HTML5 navigation."
  [routes]
  (accountant/configure-navigation!
   {:nav-handler  (fn [path]
                    (let [match  (bidi/match-route routes path)
                          page   (:handler match)
                          params (merge (parse-query-params path)
                                   (:route-params match))]
                      (rf/dispatch [:route/change page params])))
    :path-exists? (fn [path]
                    (boolean (bidi/match-route routes path)))})
  (accountant/dispatch-current!))


(defn split-keyword
  "Split a keyword into a vector of keys, e.g.

  (key->path :foo.bar.baz/quux) ; => [:foo :bar :baz :quux]"
  [page]
  (if-let [p (and page (namespace page))]
    (conj (->> (string/split p #"\.") (map keyword) vec) (keyword (name page)))
    [page]))


(defn route-dispatch
  "Route-based dispatch for multimethods."
  [multimethod {:keys [page path] :as route}]
  (if (contains? (methods multimethod) page)
    page
    (first path)))


;; ==============================================================================
;; reframe ======================================================================
;; ==============================================================================


(rf/reg-fx
 :route
 (fn [new-route]
   (let [parsed (uri new-route)]
     (if-let [query (:query parsed)]
       (accountant/navigate! (:path parsed) query)
       (accountant/navigate! new-route)))))


(defn install-events-handlers! [dispatches-fn]
  (rf/reg-event-fx
   :route/change
   (fn [{:keys [db]} [_ page-key params]]
     (let [route (merge
                  (:route db)
                  {:page   page-key
                   :path   (split-keyword page-key)
                   :params params})]
       {:db         (assoc db :route route)
        :dispatch-n (dispatches-fn route)}))))
