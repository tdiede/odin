(ns admin.server
  (:require [admin.config :as config :refer [config]]
            [admin.routes :as routes]
            [clojure.string :as string]
            [mount.core :refer [defstate]]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.prime :as optimus]
            [optimus.strategies :as strategies]
            [org.httpkit.server :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))

;; =============================================================================
;; Middleware
;; =============================================================================


(defn wrap-logging
  "Middleware to log requests."
  [handler]
  (fn [{:keys [uri request-method identity remote-addr] :as req}]
    (when-not (or (= uri "/favicon.ico")
                  (string/starts-with? uri "/assets")
                  (string/starts-with? uri "/bundles"))
      (timbre/info :web/request
                   (tb/assoc-when
                    {:uri         uri
                     :method      request-method
                     :remote-addr remote-addr}
                    :user (:account/email identity))))
    (handler req)))


(defn wrap-deps [handler deps]
  (fn [req]
    (handler (assoc req :deps deps))))


(def optimus-bundles
  {"admin.js"   ["/js/cljs/admin.js"]
   "antd.css"   ["/assets/css/antd.css"]
   "styles.css" ["/assets/css/styles.css"]})


(defn- assemble-assets []
  (concat
   (assets/load-bundles "public" optimus-bundles)
   ;; (assets/load-assets "public" [#"/assets/img/*"])
   ))


(defn app-handler [deps]
  (let [[optimize strategy] (if (config/development? config)
                              [optimizations/none strategies/serve-live-assets]
                              [optimizations/all strategies/serve-frozen-assets])]
    (-> routes/routes
        (optimus/wrap assemble-assets optimize strategy)
        (wrap-deps deps)
        ;; (wrap-authorization auth-backend)
        ;; (wrap-authentication auth-backend)
        (wrap-logging)
        (wrap-keyword-params)
        (wrap-nested-params)
        (wrap-restful-format)
        (wrap-params)
        (wrap-multipart-params)
        (wrap-resource "public")
        #_(wrap-session {:store        (datomic-store (:conn deps) :session->entity session->entity)
                       :cookie-name  (config/cookie-name config)
                       :cookie-attrs {:secure (config/secure-sessions? config)}})
        (wrap-content-type)
        (wrap-not-modified))))


;; =============================================================================
;; State
;; =============================================================================


(defn- start-server [port handler]
  (timbre/infof "webserver is starting on port %s" port)
  (httpkit/run-server handler {:port port :max-body (* 20 1024 1024)}))


(defn- stop-server [server]
  (timbre/info "webserver is shutting down")
  (server))


(defstate web-server
  :start (->> (app-handler {})
              (start-server (config/webserver-port config)))
  :stop (stop-server web-server))
