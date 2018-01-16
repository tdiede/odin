(ns odin.utils.dispatch
  "Utilities for working with multimethod dispatch."
  (:require [toolbelt.core :as tb]))


(defn- prefix-role [role key]
  (let [role' (name role)]
    (if-let [ns (and key (namespace key))]
      (keyword (str role'  "." ns) (name key))
      (keyword role' (name key)))))


;; (defn role-dispatch
;;   [multimethod role key]
;;   (let [pr (prefix-role role key)]
;;     (if (contains? (methods multimethod) pr)
;;       pr
;;       key)))


(defn role-dispatch
  [multimethod {:keys [requester page path]}]
  (let [role (:role requester)
        pr1  (prefix-role role page)
        pr2  (prefix-role role (first path))]
   (cond
     (contains? (methods multimethod) pr1)  pr1
     (contains? (methods multimethod) pr2)  pr2
     (contains? (methods multimethod) page) page
     :otherwise                             (first path))))
