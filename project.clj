(defproject odin "0.1.0-SNAPSHOT"
  :description "The all-seing dashboard."
  :url "http://my.joinstarcity.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.clojure/test.check "0.9.0"]
                 ;; Web
                 [bidi "2.1.2"]
                 [ring "1.6.2"]
                 [starcity/facade "0.1.2" :exclusions [com.google.guava/guava]]
                 [starcity/datomic-session-store "0.1.0"]
                 [starcity/customs "0.1.0" :exclusions [com.google.guava/guava
                                                        com.fasterxml.jackson.core/jackson-core]]
                 [optimus "0.19.3"]
                 [http-kit "2.2.0"]
                 [compojure "1.6.0" :exclusions [ring/ring-core]]
                 [ring-middleware-format "0.7.2" :exclusions [ring/ring-core]]
                 ;; GraphQL
                 [com.walmartlabs/lacinia "0.20.0"]
                 [vincit/venia "0.2.2"]
                 ;; CLJS
                 [tongue "0.2.2"]       ; i18n
                 [antizer "0.2.2" :exclusions [com.google.guava/guava cljsjs/react]]
                 [re-frame "0.9.4" :exclusions [com.google.guava/guava cljsjs/react]]
                 [reagent "0.7.0"]
                 [cljsjs/highcharts "5.0.14-0"]
                 [cljsjs/highcharts-css "5.0.10-0"]
                 [cljsjs/libphonenumber "8.4.1-1"]
                 [cljsjs/react "15.6.1-0"]
                 [cljsjs/react-dom "15.6.1-0"]
                 [venantius/accountant "0.2.0" :exclusions [org.clojure/clojurescript
                                                            org.clojure/core.async]]
                 [day8.re-frame/http-fx "0.1.4" :exclusions [com.google.guava/guava
                                                             org.apache.httpcomponents/httpclient]]
                 [starcity.re-frame/stripe-fx "0.1.0"]
                 [cljsjs/moment "2.17.1-1"]

                 ;; DB
                 [starcity/blueprints "1.11.0-SNAPSHOT" :exclusions [com.datomic/datomic-free
                                                                     com.andrewmcveigh/cljs-time
                                                                     com.google.guava/guava]]
                 ;; dep resolution
                 [com.google.guava/guava "21.0"]
                 [cljs-ajax "0.6.0"]
                 ;; Util
                 [starcity/ribbon "0.7.0-SNAPSHOT"]
                 [com.cemerick/url "0.1.1"]
                 [mount "0.1.11"]
                 [aero "1.1.2"]
                 [starcity/drawknife "0.2.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.taoensso/timbre "4.10.0"]
                 [starcity/toolbelt "0.1.7" :exclusions [com.datomic/datomic-free
                                                         org.apache.httpcomponents/httpclient
                                                         com.andrewmcveigh/cljs-time
                                                         com.google.guava/guava]]]

  :jvm-opts ["-server"
             "-Xmx2g"
             "-XX:+UseCompressedOops"
             "-XX:+DoEscapeAnalysis"
             "-XX:+UseConcMarkSweepGC"]

  :repositories {"releases" {:url        "s3://starjars/releases"
                             :username   :env/aws_access_key
                             :passphrase :env/aws_secret_key}}

  :plugins [[lein-cljsbuild "1.1.4"]
            [s3-wagon-private "1.2.0"]]

  :repl-options {:init-ns user}

  :clean-targets ^{:protect false} ["resources/public/js/cljs" :target-path]

  :main odin.core)
