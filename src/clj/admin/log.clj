(ns admin.log
  (:require [admin.config :as config :refer [config]]
            [starlog.core :as starlog]
            [taoensso.timbre :as timbre]
            [mount.core :refer [defstate]]))


(defstate logger
  :start (timbre/merge-config!
          (starlog/configuration (config/log-level config)
                                 (config/log-appender config)
                                 (config/log-file config))))
