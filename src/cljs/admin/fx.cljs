(ns admin.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            cljsjs.filesaverjs
            [taoensso.timbre :as timbre]))


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
       (timbre/error e)
       (when (some? on-failure)
         (dispatch (conj on-failure e)))))))


(defn- inject-css! [href]
  (let [element (.getElementById js/document href)]
    (when (nil? element)
      (let [head (aget (.getElementsByTagName js/document "head") 0)
            link (.createElement js/document "link")]
        (aset link "id" href)
        (aset link "rel" "stylesheet")
        (aset link "type" "text/css")
        (aset link "href" href)
        (aset link "media" "all")
        (.appendChild head link)))))


(reg-fx
 :inject-css
 (fn [stylesheets]
   (cond
     (sequential? stylesheets) (doseq [s stylesheets] (inject-css! s))
     (string? stylesheets)     (inject-css! stylesheets)
     :otherwise                (throw (js/Error. "must provide string or seq of strings as css hrefs.")))))
