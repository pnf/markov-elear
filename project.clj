(defproject markov-elear "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [overtone/at-at "1.2.0"]
                 [twitter-api "1.8.0"]
                 [environ "1.1.0"]]
  :main markov-elear.generator
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:dev {:plugins [[com.jakemccrary/lein-test-refresh "0.22.0"]]}})
