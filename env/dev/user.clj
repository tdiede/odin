(ns user
  (:require
   [blueprints.models.account :as account]
   [blueprints.models.application :as application]
   [blueprints.models.customer :as customer]
   [blueprints.models.events :as events]
   [blueprints.models.license :as lincese]
   [blueprints.models.member-license :as ml]
   [blueprints.models.note :as note]
   [blueprints.models.order :as order]
   [blueprints.models.payment :as payment]
   [blueprints.models.promote :as promote]
   [blueprints.models.property :as property]
   [blueprints.models.security-deposit :as deposit]
   [blueprints.models.service :as service]
   [blueprints.models.unit :as unit]
   [clojure.spec.test :as stest]
   [clojure.tools.namespace.repl :refer [refresh]]
   [datomic.api :as d]
   [figwheel-sidecar.repl-api :as ra]
   [mount.core :as mount :refer [defstate]]
   [odin.datomic :refer [conn]]
   [odin.config :as config :refer [config]]
   [odin.core]
   [odin.seed :as seed]
   [reactor.reactor :as reactor]
   [taoensso.timbre :as timbre]
   [clojure.core.async :as a]))


(timbre/refer-timbre)


;; =============================================================================
;; Reloaded
;; =============================================================================


(def start #(mount/start-with-args {:env :dev}))


(def stop mount/stop)


(defn- in-memory-db?
  "There's a more robust way to do this, but it's not really necessary ATM."
  [uri]
  (clojure.string/starts-with? uri "datomic:mem"))


(defstate seeder
  :start (when (in-memory-db? (config/datomic-uri config))
           (timbre/debug "seeding dev database...")
           (seed/seed conn)))


(defstate reactor
  :start (let [conf {:mailer          {:api-key (config/mailgun-api-key config)
                                       :domain  (config/mailgun-domain config)
                                       :sender  (config/mailgun-sender config)
                                       :send-to "josh@starcity.com"}
                     :slack           {:webhook-url (config/slack-webhook-url config)
                                       :username    (config/slack-username config)
                                       :channel     "#debug"}
                     :stripe          {:secret-key (config/stripe-secret-key config)}
                     :public-hostname "http://localhost:8080"}
               chan (a/chan (a/sliding-buffer 512))]
           (reactor/start! conn chan conf))
  :stop (reactor/stop! reactor))


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
