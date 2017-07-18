(defproject admin "0.1.0-SNAPSHOT"
  :description "Starcity's admin dashboard."
  :url "http://admin.joinstarcity.com"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.nrepl "0.2.13"]
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
                 ;; CLJS
                 [antizer "0.2.1" :exclusions [com.google.guava/guava cljsjs/react]]
                 [re-frame "0.9.4" :exclusions [com.google.guava/guava cljsjs/react]]
                 [reagent "0.7.0"]
                 [cljsjs/react "15.6.1-0"]
                 [cljsjs/react-dom "15.6.1-0"]
                 [venantius/accountant "0.2.0" :exclusions [org.clojure/clojurescript
                                                            org.clojure/core.async]]
                 [day8.re-frame/http-fx "0.1.4" :exclusions [com.google.guava/guava]]
                 [cljsjs/moment "2.17.1-1"]
                 ;; DB
                 [starcity/blueprints "1.9.0" :exclusions [com.datomic/datomic-free
                                                           com.andrewmcveigh/cljs-time
                                                           com.google.guava/guava]]
                 ;; Util
                 [mount "0.1.11"]
                 [aero "1.1.2"]
                 [starcity/drawknife "0.1.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.taoensso/timbre "4.10.0"]
                 [starcity/toolbelt "0.1.7" :exclusions [com.datomic/datomic-free
                                                         com.andrewmcveigh/cljs-time
                                                         com.google.guava/guava]]
                 ;; dep resolution
                 [com.google.guava/guava "21.0"]]

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

  :cooper {"main" ["sass" "--watch" "-E" "UTF-8" "style/sass/main.sass:resources/public/assets/css/styles.css"]
           "antd" ["less-watch-compiler" "style/less" "resources/public/assets/css/"]}

  :main admin.core)
