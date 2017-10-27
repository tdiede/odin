{:dev {:source-paths ["src/clj" "src/cljs" "env/dev" "env/seed"]
       :plugins      [[lein-figwheel "0.5.11" :exclusions [org.clojure/clojure org.clojure/core.async]]
                      [lein-cooper "1.2.2" :exclusions [org.clojure/clojure]]]
       :dependencies [[figwheel-sidecar "0.5.11" :exclusions [ring/ring-core com.google.guava/guava]]
                      [binaryage/devtools "0.9.4"]
                      [com.datomic/datomic-free "0.9.5544"]
                      [devcards "0.2.3"]
                      [starcity/reactor "0.6.1-SNAPSHOT"]]
       :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

       :cooper {"main" ["sass" "--watch" "-E" "UTF-8" "style/sass/main.sass:resources/public/assets/css/styles.css"]
                "antd" ["less-watch-compiler" "style/less" "resources/public/assets/css/"]}}

 :uberjar {:aot          :all
           :main         odin.core
           :source-paths ["src/clj" "src/cljs" "env/seed"]
           :prep-tasks   ["compile" ["cljsbuild" "once"]]

           :dependencies [[com.datomic/datomic-pro "0.9.5544" :exclusions [com.google.guava/guava]]
                          [org.postgresql/postgresql "9.4.1211"]]

           :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                            :username :env/datomic_username
                                            :password :env/datomic_password}}

           :cljsbuild
           {:builds [{:id           "odin"
                      :source-paths ["src/cljs/odin" "src/cljs/iface"]
                      :jar          true
                      :compiler     {:main             odin.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     false
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/out"
                                     :output-dir       "resources/public/js/cljs/out"
                                     :output-to        "resources/public/js/cljs/odin.js"
                                     :externs          ["externs/stripe.ext.js"
                                                        "externs/highcharts.ext.js"]
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}]}}}
