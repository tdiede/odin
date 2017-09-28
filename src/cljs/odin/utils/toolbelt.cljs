(ns odin.utils.toolbelt)

(defn comp-alphabetical [key]
  (let [key (name key)]
    (fn [a b]
      (compare (aget a key) (aget b key)))))

(defn thing->column [key thing]
  (assoc thing :key key))
