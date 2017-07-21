(ns admin.datomic
  (:require [admin.config :as config :refer [config]]
            [datomic.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            ))


;; (defn- new-connection [uri]
;;   (timbre/info ::connecting {})
;;   (d/create-database uri)
;;   (let [conn (d/connect uri)]
;;     (db/conform-db conn :db.part/starcity)
;;     (seed/seed conn (:env (mount/args)))
;;     conn))


;; (defn- disconnect [uri conn]
;;   (timbre/info ::disconnecting {})
;;   (d/release conn))


;; (defstate conn
;;   :start (new-connection (config/datomic-uri config))
;;   :stop (disconnect conn))
