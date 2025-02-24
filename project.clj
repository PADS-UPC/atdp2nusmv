(defproject atd-reasoner "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [edu.upc/atdlib "0.1.0"]
                 [instaparse "1.4.9"]
                 [me.raynes/conch "0.8.0"]]
  :main ^:skip-aot atd-reasoner.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
