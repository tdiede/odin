(ns odin.teller
  (:require [teller.core :as teller]
            [mount.core :refer [defstate]]
            [odin.config :as config :refer [config]]
            [taoensso.timbre :as timbre]))


;; ==============================================================================
;; state ========================================================================
;; ==============================================================================


(defstate teller
  :start (let [dt   (teller/datomic-connection
                     (config/datomic-uri config)
                     (config/datomic-partition config))
               st   (teller/stripe-connection
                     (config/stripe-secret-key config))
               conn (teller/connection dt st)]
           (timbre/info "connecting to teller...")
           (teller/connect conn))
  :stop (do
          (timbre/info "disconnecting from teller...")
          (teller/disconnect teller)))
