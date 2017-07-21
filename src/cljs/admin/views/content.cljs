(ns admin.views.content
  (:require [antizer.reagent :as ant]))


(defmulti view (fn [route] (:root route)))


(defmethod view :default [{:keys [page root params]}]
  [ant/card {:title "View not found"}
   [:p [:b "Page:"] page]
   [:p [:b "Root:"] root]
   [:p [:b "Params:"] params]])
