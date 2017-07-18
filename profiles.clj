{:dev {:source-paths ["src/clj" "src/cljs" "env/dev"]
       :plugins      [[lein-figwheel "0.5.11" :exclusions [org.clojure/clojure org.clojure/core.async]]
                      [lein-cooper "1.2.2" :exclusions [org.clojure/clojure]]]
       :dependencies [[figwheel-sidecar "0.5.11" :exclusions [ring/ring-core com.google.guava/guava]]
                      [binaryage/devtools "0.9.4"]
                      [com.datomic/datomic-free "0.9.5544"]]
       :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}

 :uberjar {:aot          :all
           :main         admin.core
           :source-paths ["src/clj" "src/cljs"]
           :prep-tasks   ["compile" ["cljsbuild" "once"]]

           :dependencies [[com.datomic/datomic-pro "0.9.5544" :exclusions [com.google.guava/guava]]
                          [org.postgresql/postgresql "9.4.1211"]]

           :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                            :username :env/datomic_username
                                            :password :env/datomic_password}}

           :cljsbuild
           {:builds [{:id           "admin"
                      :source-paths ["src/cljs/admin" "src/cljs/starcity"]
                      :jar          true
                      :compiler     {:main             admin.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     false
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/out"
                                     :output-dir       "resources/public/js/cljs/out"
                                     :output-to        "resources/public/js/cljs/admin.js"
                                     :externs          ["externs/stripe.ext.js"]
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}]}}}
