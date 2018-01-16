(ns odin.services.member.db
  (:require [odin.routes :as routes]))


(def path ::services)


(def default-params
  {:category :all})


(def default-value
  {path {:params default-params}})


(defmulti params->route (fn [page params] page))


(defmethod params->route :services/book [page params]
  (let [params (-> (select-keys params [:category])
                   (update :category name))]
    (routes/path-for page :query-params params)))


(defmethod params->route :services/manage [page params]
  (routes/path-for page))


(defmulti parse-query-params (fn [page params] page))


(defmethod parse-query-params :services/book [page params]
  (update params :category keyword))


(defmethod parse-query-params :services/manage [page params]
  params)
