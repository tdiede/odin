(ns user
  (:require [admin.core]
            [clojure.spec.test.alpha :as stest]
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
    (ra/start-figwheel! "admin")))


(defn cljs-repl []
  (ra/cljs-repl "admin"))
