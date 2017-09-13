(ns odin.utils.dispatch
  "Utilities for working with multimethod dispatch.")


(defn- prefix-role [role key]
  (let [role' (name role)]
    (if-let [ns (and key (namespace key))]
      (keyword (str role'  "." ns) (name key))
      (keyword role' (name key)))))


(defn role-dispatch
  [multimethod role key]
  (let [pr (prefix-role role key)]
    (if (contains? (methods multimethod) pr)
      pr
      key)))
