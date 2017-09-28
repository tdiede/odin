(ns odin.graphql.authorization
  (:require [com.walmartlabs.lacinia.resolve :as resolve]))


(defmulti authorized?
  "Is `requester` authorized to access the resolver identified by
  `resolver-key`?"
  (fn [ctx requester params]
    (:resolver ctx)))


(defmethod authorized? :default [_ _ _] true)


(defn- unauthorized-response
  [resolver-key]
  {:message  "You are not authorized to access this resource."
   :resource resolver-key
   :reason   :unauthorized})


(defn wrap-authorize
  [key resolver]
  (fn [{requester :requester :as ctx} params val]
    (if (authorized? (assoc ctx :resolver key) requester params)
      (resolver ctx params val)
      (resolve/resolve-as nil (unauthorized-response key)))))
