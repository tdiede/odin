(ns iface.table
  (:require [reagent.core :as r]))

(defn wrap-cljs
  "TODO:"
  [f]
  (fn [x record]
    (r/as-element (f x (js->clj record :keywordize-keys true)))))
