(ns markov-elear.generator
  (:require [overtone.at-at :as overtone]
            [twitter.api.restful :as twitter]
            [twitter.oauth :as twitter-oauth]
            [environ.core :refer [env]]))

(defn chain->text [chain]
  (apply str (interpose " " chain)))

(defn walk-chain [nmin nmax prefix chain result]
  (let [prefixes (conj (map #(take-last % prefix) (range (dec nmin) nmax)) prefix)
        tries    (map chain prefixes)
        suffixes (apply clojure.set/union tries)
        ;; _ (println prefix (map count tries))
        ]
    (if (empty? suffixes)
      result
      (let [suffix (first (shuffle suffixes))
            ;; new prefix grows to length nmax
            new-prefix (concat (take-last (dec nmax) prefix) [suffix])
            result-with-spaces (chain->text result)
            result-char-count (count result-with-spaces)
            suffix-char-count (+ 1 (count suffix))
            new-result-char-count (+ result-char-count suffix-char-count)]
        (if (>= new-result-char-count 140)
          result
          (recur nmin nmax new-prefix chain (conj result suffix)))))))

(defn generate-text
  [nmin nmax start-phrase word-chain]
  (let [prefix (clojure.string/split start-phrase #" ")
        result-chain (walk-chain nmin nmax prefix word-chain prefix)
        result-text (chain->text result-chain)]
    result-text))


(defn file->ngrams [nmin nmax fname]
  (let [ws (-> (clojure.java.io/resource fname)
               slurp
               (clojure.string/split #"[\s|\n]"))
        pnmax  (partition-all nmax 1 ws)
        prest  (for [i (range nmin nmax)] (map (partial take i) pnmax))]
    (apply concat pnmax prest)))


(def files ["quangle-wangle.txt" "monad.txt" "clojure.txt" "functional.txt"
            "jumblies.txt" "pelican.txt" "pobble.txt"])

(defn ngrams->rules [ngrams]
  (reduce
   (fn [m ngram]
     (let [[w & kr] (reverse ngram)
           k (reverse kr)
           v (m k)]
       (assoc m k (if v (conj v w) #{w}))))
   {} ngrams))


(defn rules [nmin nmax] (ngrams->rules (apply concat (map (partial file->ngrams nmin nmax) files))))

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

(defn tweet-text [nmin nmax]
  (let [rs (rules nmin nmax)
        text (generate-text nmin nmax (-> prefix-list shuffle first) rs)]
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

