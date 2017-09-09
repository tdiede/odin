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
    (ra/start-figwheel!)))


(defn cljs-repl [& [build]]
  (ra/cljs-repl (or build "odin")))


(defn go! []
  (go)
  (start-figwheel!)
  (timbre/debug "⟡ WE ARE GO! ⟡"))
