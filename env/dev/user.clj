(ns user
  (:require [odin.core]
            [odin.config :as config :refer [config]]
            [odin.datomic :refer [conn]]
            [odin.seed :as seed]
            [clojure.spec.test :as stest]
            [clojure.tools.namespace.repl :refer [refresh]]
            [figwheel-sidecar.repl-api :as ra]
            [mount.core :as mount :refer [defstate]]
            [taoensso.timbre :as timbre]))


(timbre/refer-timbre)


;; =============================================================================
;; Reloaded
;; =============================================================================


(defn- in-memory-db? []
  (= "datomic:mem://localhost:4334/starcity" (config/datomic-uri config)))


(defstate seed
  :start (when (in-memory-db?)
           (timbre/debug "seeding dev database...")
           (seed/seed conn)))


(def start #(mount/start-with-args {:env :dev}))


(def stop mount/stop)


(defn go []
  (start)
  (stest/instrument)
  :ready)


(defn reset []
  (stop)
  (refresh :after 'user/go))


;; =============================================================================
;; Figwheel
;; =============================================================================


(defn start-figwheel! []
  (when-not (ra/figwheel-running?)
    (timbre/debug "starting figwheel server...")
    (ra/start-figwheel! "odin")))


(defn cljs-repl []
  (ra/cljs-repl "odin"))


(defn go! []
  (go)
  (start-figwheel!)
  (timbre/debug "⟡ WE ARE GO! ⟡"))
