{:dev {:source-paths ["src/clj" "src/cljs" "env/dev" "env/seed"]
       :plugins      [[lein-figwheel "0.5.14" :exclusions [org.clojure/clojure org.clojure/core.async]]
                      [lein-cooper "1.2.2" :exclusions [org.clojure/clojure]]]
       :dependencies [[figwheel-sidecar "0.5.14" :exclusions [org.clojure/clojurescript
                                                              ring/ring-core
                                                              com.google.guava/guava]]
                      [binaryage/devtools "0.9.9"
                       :exclusions [org.clojure/clojurescript]]
                      [com.datomic/datomic-free "0.9.5544"]
                      [devcards "0.2.4"
                       :exclusions [org.clojure/clojurescript
                                    org.clojure/core.async]]
                      [starcity/reactor "1.4.0"
                       :excluions [ring cheshire clj-time org.apache.httpcomponents/httpcore commons-codec
                                   org.clojure/core.async]]]
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
           {:builds [{:id           "admin"
                      :source-paths ["src/cljs/admin" "src/cljs/iface"]
                      :jar          true
                      :compiler     {:main             admin.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     true
                                     :pseudo-names     true
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/out"
                                     :output-dir       "resources/public/js/cljs/out"
                                     :output-to        "resources/public/js/cljs/admin.js"
                                     :source-map       "resources/public/js/cljs/admin.js.map"
                                     :externs          ["externs/highcharts.ext.js"]
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}

                     {:id           "member"
                      :source-paths ["src/cljs/member" "src/cljs/iface"]
                      :jar          true
                      :compiler     {:main             member.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     true
                                     :pseudo-names     true
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/out"
                                     :output-dir       "resources/public/js/cljs/out"
                                     :output-to        "resources/public/js/cljs/member.js"
                                     :source-map       "resources/public/js/cljs/member.js.map"
                                     :externs          ["externs/stripe.ext.js"]
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}


                     {:id           "onboarding"
                      :source-paths ["src/cljs/onboarding" "src/cljs/iface"]
                      :jar          true
                      :compiler     {:main             onboarding.core
                                     :optimizations    :advanced
                                     :elide-asserts    true
                                     :pretty-print     true
                                     :pseudo-names     true
                                     :parallel-build   true
                                     :asset-path       "/js/cljs/onboarding/out"
                                     :output-dir       "resources/public/js/cljs/onboarding/out"
                                     :output-to        "resources/public/js/cljs/onboarding.js"
                                     :source-map       "resources/public/js/cljs/onboarding.js.map"
                                     :externs          ["externs/stripe.ext.js"
                                                        "externs/chatlio.ext.js"]
                                     :closure-warnings {:externs-validation :off
                                                        :non-standard-jsdoc :off}}}]}}}
