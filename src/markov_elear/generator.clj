(ns markov-elear.generator
  (:require [overtone.at-at :as overtone]
            [twitter.api.restful :as twitter]
            [twitter.oauth :as twitter-oauth]
            [environ.core :refer [env]]))

(defn chain->text [chain]
  (apply str (interpose " " chain)))

(defn walk-chain [prefix chain result]
  (let [suffixes (get chain prefix)]
    (if (empty? suffixes)
      result
      (let [suffix (first (shuffle suffixes))
            new-prefix [(last prefix) suffix]
            result-with-spaces (chain->text result)
            result-char-count (count result-with-spaces)
            suffix-char-count (+ 1 (count suffix))
            new-result-char-count (+ result-char-count suffix-char-count)]
        (if (>= new-result-char-count 140)
          result
          (recur new-prefix chain (conj result suffix)))))))

(defn generate-text
  [start-phrase word-chain]
  (let [prefix (clojure.string/split start-phrase #" ")
        result-chain (walk-chain prefix word-chain prefix)
        result-text (chain->text result-chain)]
    result-text))


(defn file->ngrams [n fname ]
  (partition-all n 1
                 (-> (clojure.java.io/resource fname)
                     slurp
                     (clojure.string/split #"[\s|\n]"))))


(def files ["quangle-wangle.txt" "monad.txt" "clojure.txt" "functional.txt"
            "jumblies.txt" "pelican.txt" "pobble.txt"])

(defn ngrams->rules [ngrams]
  (reduce
   (fn [m ngram]
     (let [[a b c] ngram
           k [a b]
           v (m k)]
       (assoc m k (if v (conj v c) #{c}))))
   {} ngrams))


(def functional-leary (ngrams->rules (apply concat (map (partial file->ngrams 3) files))))

(def prefix-list ["On the" "They went" "And all" "We think"
                  "For every" "No other" "To a" "And every"
                  "We, too," "For his" "And the" "But the"
                  "Are the" "The Pobble" "For the" "When we"
                  "In the" "Yet we" "With only" "Are the"
                  "Though the"  "And when"
                  "We sit" "And this" "No other" "With a"
                  "And at" "What a" "Of the"
                  "O please" "So that" "And all" "When they"
                  "But before" "Whoso had" "And nobody" "And it's"
                  "For any" "For example," "Also in" "In contrast"])


(defn end-at-last-punctuation [text]
  (let [trimmed-to-last-punct (apply str (re-seq #"[\s\w]+[^.!?,]*[.!?,]" text))
        trimmed-to-last-word (apply str (re-seq #".*[^a-zA-Z]+" text))
        result-text (if (empty? trimmed-to-last-punct)
                      trimmed-to-last-word
                      trimmed-to-last-punct)
        cleaned-text (clojure.string/replace result-text #"[,| ]$" ".")]
    (clojure.string/replace cleaned-text #"\"" "'")))

(defn tweet-text []
  (let [text (generate-text (-> prefix-list shuffle first) functional-leary)]
    (end-at-last-punctuation text)))


(def my-creds (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                              (env :app-consumer-secret)
                                              (env :user-access-token)
                                              (env :user-access-secret)))

(defn status-update []
  (let [tweet (tweet-text)]
    (println "generated tweet is :" tweet)
    (println "char count is:" (count tweet))
    (when (not-empty tweet)
      (try (twitter/statuses-update :oauth-creds my-creds
                                    :params {:status tweet})
           (catch Exception e (println "Oh no! " (.getMessage e)))))))

(def my-pool (overtone/mk-pool))

(defn -main [& args]
  ;; every 8 hours
  (println "Started up")
  (println (tweet-text))
  (overtone/every (* 1000 60 60 8) #(println (status-update)) my-pool))

