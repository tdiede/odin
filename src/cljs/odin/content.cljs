(ns odin.content
  (:require [antizer.reagent :as ant]
            [toolbelt.core :as tb]))


(defn- role-path [{:keys [requester path] :as route}]
  (let [role (-> requester :role name)
        path (first path)]
    (if-let [ns (namespace path)]
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


(comment
  (methods view)

  )
