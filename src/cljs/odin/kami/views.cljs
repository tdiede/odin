(ns odin.kami.views
  (:require [antizer.reagent :as ant]
            [iface.card :as card]
            [iface.typography :as typography]
            [odin.content :as content]
            [odin.routes :as routes]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [odin.utils.formatters :as format]
            [reagent.core :as r]))

;; (rf/reg-event-fx
;;  ::init-street-view
;;  (fn [_ _]
;;    {:load-scripts ["https://maps.googleapis.com/maps/api/js?key=AIzaSyC4by7S33pwsfXRyOUbXMYOzQLNRHfeQuI"]}))


;; (defn street-view []
;;   (r/create-class
;;    {:component-will-mount
;;     (fn [_]
;;       (dispatch [::init-street-view]))
;;     :component-did-mount
;;     (fn [this]
;;       (let ))
;;     :reagent-render
;;     (fn []
;;       [:div#street-view])}))


(defn result-address
  [{:keys [addr] :as params} {:keys [address eas_baseid]}]
  [card/selectable
   {:active (= addr eas_baseid)
    :href   (routes/path-for :kami :query-params (merge params {:addr eas_baseid}))}
   [:p.bold address]])


(defn- search-results []
  (let [is-loading   (subscribe [:loading? :kami/search])
        addresses    (subscribe [:kami/addresses])
        query-params (subscribe [:kami/query-params])]
    [:div.mt3
     (if @is-loading
       [:div.has-text-centered
        {:style {:padding-top 40}}
        [ant/spin]]
       (doall
        (map-indexed
         #(with-meta [:div.mb1 [result-address @query-params %2]] {:key %1})
         @addresses)))]))


(defn- search-bar []
  (let [query (subscribe [:kami/query])]
    [ant/input-search {:placeholder   "input address"
                       :size          "large"
                       :default-value @query
                       :on-search     #(dispatch [:kami/query %])}]))


(defn- report-title [address]
  [:b "Report for " (:address address)])


(defn- get-score-sum [scores]
  (str (->> scores (map second) (apply +))
       " / "
       (* (count scores) 3)))


(defn- format-reason
  [k]
  (let [reason (k (:criteria @(subscribe [:kami/report])))]
    (if (nil? reason)
      "No data"
      (case k
        :last-buyout-filed      (format/get-time-ago reason)
        :last-planning-activity (format/get-time-ago reason)
        :last-permit-created    (format/get-time-ago reason)
        :square-feet            (format/number reason)
        reason))))


(defn- get-score-class
  [score]
  (case score
    0 "score-0"
    1 "score-1"
    2 "score-2"
    3 "score-3"
    "score-none"))


(defn- score-summary []
  (let [kami (subscribe [:kami/report])]
    (fn []
      (let [{:keys [scores criteria]} @kami]
        [:div
         [:p [:span "Total Score: "] [:b (get-score-sum scores)]]
         (->> (map #(vector :li.enum-item
                            [:span.enum-label %]
                            [:span.enum-value {:class (get-score-class (% scores))}
                             (% scores)]
                            [:span.enum-reason (format-reason %)])
                   (keys scores))
              (into [:ul.enum-list]))]))))


(defn- report-view []
  (let [is-loading (subscribe [:loading? :kami/score])
        address    (subscribe [:kami/selected-address])
        report     (subscribe [:kami/report])]
    (let [scores (:scores @report)]
      [ant/card {:title   (r/as-element (report-title @address))
                 :loading @is-loading}
        [score-summary]])))


(defn- content-view []
  (let [address-id (subscribe [:kami/address-id])]
    [:div
     (typography/view-header "Kami" "Determine a building's potential as a Starcity.")

     [:div.columns
      [:div.column.is-half
       [search-bar]
       [search-results]]
      [:div.column
       (when (some? @address-id)
         [report-view])]]]))


(defmethod content/view :kami [route]
  [content-view])
