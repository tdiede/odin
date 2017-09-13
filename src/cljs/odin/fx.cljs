(ns odin.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            cljsjs.filesaverjs
            [toolbelt.core :as tb]))


(defn- download-csv! [filename content]
  (let [mime-type "text/csv;charset=utf-8"
        content   (if (sequential? content) content [content])
        blob      (new js/Blob (clj->js content) #js {:type mime-type})]
    (js/saveAs blob filename)))


(defn- csv-rows [keys data]
  (->> data
       (map (apply juxt keys))
       (into [keys])
       (transduce (comp (map #(map clj->js %))
                     (map #(interpose "," %))
                     (map (partial apply str))
                     (map #(str % "\n")))
                  conj
                  [])))


(reg-fx
 :export-csv
 (fn [{:keys [filename headers rows on-success on-failure]}]
   (try
     (->> (csv-rows headers rows) (download-csv! filename))
     (when (some? on-success)
       (dispatch on-success))
     (catch js/Error e
       (tb/error e)
       (when (some? on-failure)
         (dispatch (conj on-failure e)))))))
