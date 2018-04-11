(ns odin.routes.util
  (:require [datomic.api :as d]))


;; =============================================================================
;; Deps
;; =============================================================================


(defn ->conn [req]
  (get-in req [:deps :conn]))


(defn ->db [req]
  (d/db (get-in req [:deps :conn])))


(defn ->stripe [req]
  (get-in req [:deps :stripe]))


(defn ->requester [req]
  (let [id (get-in req [:identity :db/id])]
    (d/entity (->db req) id)))


(defn ->config [req]
  (get-in req [:deps :config]))


(defn ->teller [req]
  (get-in req [:deps :teller]))
