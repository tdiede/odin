(ns odin.content
  (:require [antizer.reagent :as ant]))


(defmulti view (fn [{path :path}] (first path)))


(defmethod view :default [{:keys [page path root params]}]
  [ant/card {:title "View not found"}
   [:p [:b "Page:"] page]
   [:p [:b "Path:"] path]
   [:p [:b "Root:"] root]
   [:p [:b "Params:"] params]])
