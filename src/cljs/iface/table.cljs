(ns iface.table
  (:require [antizer.reagent :as ant]
            [reagent.core :as r]
            [toolbelt.core :as tb]))

;; helpers ======================================================================


(defn wrap-cljs
  "TODO:"
  [f]
  (fn [x record]
    (r/as-element (f x (js->clj record :keywordize-keys true)))))


(defn maybe-render
  ([f]
   (maybe-render f "N/A"))
  ([f placeholder]
   (fn [x]
     (r/as-element (if (some? x) (f x) placeholder)))))


;; sorting ======================================================================


(defn sort-col-title
  "Component to create a table header with anchor elements for sorting. `href-fn`
  is a function of one argument that should produce a URI given query params
  with updated `sort-order` and `sort-by` keys when arrow links are clicked."
  [query-params key title href-fn]
  (r/as-element
   (let [{:keys [sort-by sort-order]} query-params]
     [:span title
      [:div.ant-table-column-sorter
       [:a.ant-table-column-sorter-up
        {:class (if (and (= sort-by key) (= sort-order :asc)) "on" "off")
         :href  (href-fn (assoc query-params :sort-order :asc :sort-by key))}
        [ant/icon {:type "caret-up"}]]
       [:a.ant-table-column-sorter-down
        {:class (if (and (= sort-by key) (= sort-order :desc)) "on" "off")
         :href  (href-fn (assoc query-params :sort-order :desc :sort-by key))}
        [ant/icon {:type "caret-down"}]]]])))


(defn sort-params->query-params [params]
  (tb/transform-when-key-exists params
    {:sort-by name :sort-order name}))


(defn query-params->sort-params [query-params]
  (tb/transform-when-key-exists query-params
    {:sort-by keyword :sort-order keyword}))


(def date-sort-comp
  {:asc #(cond
           (and (some? %1) (some? %2))
           (.isBefore (js/moment. %1) (js/moment. %2))

           (and (some? %1) (nil? %2))
           true

           :otherwise false)
   :desc #(cond
            (and (some? %1) (some? %2))
            (.isAfter (js/moment. %1) (js/moment. %2))

            (and (some? %1) (nil? %2))
            true

            :otherwise false)})


(def number-sort-comp
  {:asc < :desc >})


(defn sort-compfn
  [sort-fns path sort-order]
  (let [default (get {:asc #(compare %1 %2) :desc #(compare %2 %1)} sort-order)]
    (get-in sort-fns path default)))


(defn sort-rows
  [{sb :sort-by, so :sort-order} sort-fns rows]
  (if-let [path (get-in sort-fns [sb :path])]
    (->> rows
         ;; preprocess the row so that there's something under the path.
         (map (fn [row] (assoc row ::path (get-in row path))))
         (sort-by ::path (sort-compfn sort-fns [::path] so)))
    (sort-by sb (sort-compfn sort-fns [sb so] so) rows)))

;; TODO: Specs!
