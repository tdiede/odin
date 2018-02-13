(ns cards.utils.seed
  (:require [clojure.string :as string]
            cljsjs.moment))


(defn id []
  (js/parseInt
   (->> (repeatedly (comp inc #(rand-int 9)))
        (take 12)
        (apply str))))


(defn first-name []
  (-> "Noah Liam William Mason James Benjamin Jacob Michael Elija Ethan Emma Olivia Ava Sophia Isabella Mia Charlotte Abigail Emily Harper"
      (string/split #" ")
      (rand-nth)))


(defn last-name []
  (-> "Smith Jones Taylor Williams Brown Davies Evans Wilson Thomas Roberts Johnson Lewis Walker Robinson Wood Thompson White Watson Jackson Wright"
      (string/split #" ")
      (rand-nth)))


(defn full-name []
  (str (first-name) " " (last-name)))


(defn phone []
  (let [start (rand-nth (range 2 10))]
    (apply str start (take 9 (repeatedly #(rand-int 10))))))


(defn heads?
  "As in '`heads` or tails'?"
  []
  (rand-nth [true false]))


(def tails?
  "As in 'heads or `tails`'?

  Why?
  ...
  It's fun?"
  (comp not heads?))


(defn future-date []
  (.add (js/moment) "days" (rand-nth (range 1 366))))


(defn past-date []
  (.subtract (js/moment) "days" (rand-nth (range 1 366))))
