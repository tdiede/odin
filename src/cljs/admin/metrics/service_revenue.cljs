(ns admin.metrics.service-revenue
  (:require [antizer.reagent :as ant]
            [iface.chart :as chart]
            [reagent.core :as r]
            [re-frame.core :refer [subscribe
                                   dispatch
                                   dispatch-sync
                                   reg-event-fx
                                   reg-event-db
                                   reg-sub
                                   path]]
            [toolbelt.core :as tb]))


;; =============================================================================
;; DB
;; =============================================================================


(def db-path ::db)


(def default-value
  {:data   []
   :params {:chart-type "community"
            :from       (.startOf (js/moment.) "month")
            :to         (.endOf (js/moment.) "month")}})


;; =============================================================================
;; Subs
;; =============================================================================


(defn- sum-by-service [payments]
  (->> (group-by :service payments)
       (reduce (fn [acc [sname payments]]
                 (conj acc [sname (apply + (map :amount payments))]))
               [])))


(defn- drilldown
  [payments key]
  (->> (group-by key payments)
       (reduce (fn [acc [name payments]]
                 (conj acc {:name name
                            :id   name
                            :data (sum-by-service payments)}))
               [])))


(defn- series
  [payments key]
  (->> (group-by key payments)
       (reduce (fn [acc [name payments]]
                 (conj acc {:name      name
                            :y         (apply + (map :amount payments))
                            :drilldown name}))
               [])))


(defn- chart-config
  [payments {:keys [from to chart-type] :as params}]
  (let [x-axis-title (str (.format from "l") " - " (.format to "l"))
        key          (if (= "community" chart-type) :property :billed)]
    {:chart       {:type "column"}
     :title       nil
     :legend      {:enabled false}
     :subtitle    nil
     :credits     {:enabled false}
     :xAxis       {:type  "category"
                   :title {:text x-axis-title}}
     :yAxis       {:title {:text "Total Revenue"}}
     :plotOptions {:series {:borderWidth 0
                            :dataLabels  {:enabled true
                                          :format  "${point.y:.2f}"}}}
     :tooltip     {:headerFormat "<span style='font-size:11px'>{series.name}</span><br>"
                   :pointFormat  "<span style='color:{point.color}'>{point.name}</span>: <b>${point.y:.2f}</b> <br/>"}
     :series      [{:name         "Properties"
                    :colorByPoint true
                    :data         (series payments key)}]
     :drilldown   {:series (drilldown payments key)}}))


(reg-sub
 ::service-revenue
 (fn [db _]
   (get db db-path)))


(reg-sub
 :metrics.service-revenue/data
 :<- [::service-revenue]
 (fn [chart _]
   (:data chart)))


(reg-sub
 :metrics.service-revenue/params
 :<- [::service-revenue]
 (fn [chart _]
   (:params chart)))


(reg-sub
 :metrics.service-revenue/config
 :<- [:metrics.service-revenue/data]
 :<- [:metrics.service-revenue/params]
 (fn [[orders params] _]
   (chart-config orders params)))


;; =============================================================================
;; Events
;; =============================================================================


(reg-event-fx
 :metrics.service-revenue/bootstrap
 ;; NOTE: This is setting up the db, so we don't want to use the `path`
 ;; interceptor
 (fn [{db :db} _]
   {:db       (assoc db db-path default-value)
    :dispatch [:metrics.service-revenue/fetch]}))


(defn- transform-params [new-params]
  (let [{:keys [date-range] :as params} (js->clj new-params :keywordize-keys true)]
    (merge params {:from (first date-range) :to (second date-range)})))


(reg-event-fx
 :metrics.service-revenue/params
 [(path db-path)]
 (fn [{db :db} [_ new-params]]
   (let [new-params (transform-params new-params)]
     {:db       (update-in db [:params] merge new-params)
      :dispatch [:metrics.service-revenue/fetch]})))


(reg-event-fx
 :metrics.service-revenue/fetch
 [(path db-path)]
 (fn [{db :db} [k]]
   (let [{:keys [from to]} (get-in db [:params])]
     {:dispatch [:ui/loading k true]
      :graphql  {:query
                 [[:payments
                   {:params {:types    [:order]
                             :statuses [:paid]
                             :datekey  :paid
                             :from     (.toISOString from)
                             :to       (.toISOString to)}}
                   [:id :amount :paid_on :description
                    [:account [:name :email]]
                    [:property [:name]]
                    [:order [:id :cost [:service [:name :billed]]]]]]]
                 :on-success [::revenue-chart-success]
                 :on-failure [:graphql/failure k]}})))


(defn- parse-revenue-payment
  [{:keys [account property order] :as payment}]
  (merge
   payment
   {:name     (:name account)
    :email    (:email account)
    :billed   (get-in order [:service :billed])
    :property (:name property)
    :order-id (:id order)
    :service  (get-in order [:service :name])
    :cost     (:cost order)}))


(reg-event-fx
 ::revenue-chart-success
 [(path db-path)]
 (fn [{db :db} [k response]]
   (let [payments (->> (get-in response [:data :payments])
                       (map parse-revenue-payment))]
     {:dispatch [:ui/loading :metrics.service-revenue/fetch false]
      :db       (assoc-in db [:data] payments)})))


(def ^:private revenue-csv-keys
  [:id :order-id :property :name :email :cost :amount :billed :service :paid_on :description])


(reg-event-fx
 :metrics.service-revenue/export-csv
 [(path db-path)]
 (fn [{db :db} _]
   (let [{:keys [from to]} (get-in db [:params])
         fmt               (fn [d] (.format d "MMDDY"))
         filename          (str "revenue-" (fmt from) "-" (fmt to) ".csv")]
     {:export-csv {:filename filename
                   :headers  revenue-csv-keys
                   :rows     (:data db)}})))


;; =============================================================================
;; Views
;; =============================================================================


(defn- service-revenue-chart-options [showing]
  [ant/button {:type     "dashed"
               :icon     (if @showing "up" "down")
               :on-click #(swap! showing not)}
   "Controls"])


(defn- submit-form-change! [_ values]
  (dispatch [:metrics.service-revenue/params values]))


(defn- service-revenue-chart-controls []
  (let [params (subscribe [:metrics.service-revenue/params])]
    (fn []
      (let [form (ant/get-form)]
        [:div {:style {:padding       24
                       :background    "#fbfbfb"
                       :border        "1px solid #d9d9d9"
                       :border-radius 6
                       :margin-bottom 24}}
         [ant/form {:on-submit (fn [event]
                                 (.preventDefault event)
                                 (ant/validate-fields form submit-form-change!))}
          [:div.columns
           [:div.column
            [ant/form-item {:label "X Axis" :key :chart-type}
             (ant/decorate-field form "chart-type" {:initial-value (:chart-type @params)}
                                 [ant/select
                                  [ant/select-option {:value "community"} "Community"]
                                  [ant/select-option {:value "billing"} "Billing Type"]])]]
           [:div.column
            [ant/form-item {:label "Within Period" :key :date-range}
             (ant/decorate-field form "date-range" {:initial-value [(:from @params) (:to @params)]}
                                 [ant/date-picker-range-picker {:format "l"}])]]]
          [:div
           [ant/form-item
            [ant/button
             {:type "ghost" :on-click #(dispatch [:metrics.service-revenue/export-csv])}
             "Download CSV"]
            [ant/button {:type "primary" :html-type "submit"} "Update"]]]]]))))


(defn chart []
  (let [is-loading       (subscribe [:ui/loading? :metrics.service-revenue/fetch])
        config           (subscribe [:metrics.service-revenue/config])
        showing-controls (r/atom false)]
    (r/create-class
     {:component-will-mount
      (fn [_]
        ;; NOTE: Must use `dispatch-sync`! We need to ensure that the `default-value`
        ;; for db is configured.
        (dispatch-sync [:metrics.service-revenue/bootstrap]))
      :reagent-render
      (fn []
        [ant/card {:title   (r/as-element [:b "Helping Hands Order Revenue"])
                   :extra   (r/as-element [service-revenue-chart-options showing-controls])
                   :loading @is-loading}
         (when @showing-controls
           (r/as-element (ant/create-form (service-revenue-chart-controls))))
         [chart/chart @config]])})))
