(ns odin.teller
  (:require [teller.core :as teller]
            [mount.core :refer [defstate]]
            [odin.config :as config :refer [config]]
            [taoensso.timbre :as timbre]))


;; ==============================================================================
;; state ========================================================================
;; ==============================================================================


(defstate teller
  :start (let [conn (teller/datomic-connection
                     (config/datomic-uri config)
                     (config/stripe-secret-key config))]
           (timbre/info "connecting to teller...")
           (teller/connect conn))
  :stop (do
          (timbre/info "disconnecting from teller...")
          (teller/disconnect teller)))
