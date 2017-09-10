(ns odin.content
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


(defn- role-path [{:keys [requester path] :as route}]
  (let [role (-> requester :role name)
        path (first path)]
    (if-let [ns (and path (namespace path))]
      (keyword (str role  "." ns) path)
      (keyword role path))))


(defmulti view
  (fn [{:keys [path] :as route}]
    (let [role-path (role-path route)]
      (if (contains? (methods view) role-path)
       role-path
       (first path)))))


(defmethod view :default [{:keys [page path root params]}]
  [ant/card {:title "View not found"}
   [:p [:b "Page:"] page]
   [:p [:b "Path:"] path]
   [:p [:b "Root:"] root]
   [:p [:b "Params:"] params]])


;; This component is rendered when the user navigations to the /logout entpoint.
;; Because we need a catch-all route in `odin.routes` to prevent from hitting
;; the server on un-implemented routes, this component is rendered and
;; /immediately/ reloads the window, causing a forced server request.
(defn- logout! []
  (r/create-class
   {:component-will-mount
    (fn [_]
      (.reload js/window.location))
    :reagent-render
    (fn []
      [:div])}))


(defmethod view :logout [_] [logout!])
