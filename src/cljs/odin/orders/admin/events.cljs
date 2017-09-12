(ns odin.orders.admin.events
  (:require [cljsjs.filesaverjs]
            [odin.orders.admin.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   path]]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :admin/orders [route]
  [[:orders.admin.chart.revenue/fetch]])


(defn- transform-params [new-params]
  (let [{:keys [date-range] :as params} (js->clj new-params :keywordize-keys true)]
    (merge params {:from (first date-range) :to (second date-range)})))


(reg-event-fx
 :orders.admin.chart.revenue/params
 [(path db/path)]
 (fn [{db :db} [_ new-params]]
   (let [new-params (transform-params new-params)]
     {:db       (update-in db [:revenue :params] merge new-params)
      :dispatch [:orders.admin.chart.revenue/fetch]})))


(reg-event-fx
 :orders.admin.chart.revenue/fetch
 [(path db/path)]
 (fn [{db :db} [k]]
   (let [{:keys [from to]} (get-in db [:revenue :params])]
     {:dispatch [:loading k true]
      :graphql  {:query
                 [[:payments
                   {:params {:types    [:order]
                             :statuses [:paid]
                             :from     (.toISOString from)
                             :to       (.toISOString to)}}
                   [:id :amount :paid_on :description
                    [:account [:name :email]]
                    [:property [:name]]
                    [:order [:id [:service [:name :billed]]]]]]]
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
    :service  (get-in order [:service :name])}))


(reg-event-fx
 ::revenue-chart-success
 [(path db/path)]
 (fn [{db :db} [k response]]
   (let [payments (->> (get-in response [:data :payments])
                       (map parse-revenue-payment))]
     {:dispatch [:loading :orders.admin.chart.revenue/fetch false]
      :db       (assoc-in db [:revenue :data] payments)})))


(defn download-csv! [filename content]
  (let [mime-type "text/csv;charset=utf-8"
        content   (if (sequential? content) content [content])
        blob      (new js/Blob (clj->js content) #js {:type mime-type})]
    (js/saveAs blob filename)))


(def ^:private revenue-csv-keys
  [:id :order-id :property :name :email :amount :billed :service :paid_on :description])


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


(reg-event-fx
 :orders.admin.chart.revenue/export-csv
 [(path db/path)]
 (fn [{db :db} _]
   (let [{:keys [from to]} (get-in db [:revenue :params])
         fmt               (fn [d] (.format d "MMDDY"))
         filename          (str "revenue-" (fmt from) "-" (fmt to) ".csv")]
     (->> (get-in db [:revenue :data])
          (csv-rows revenue-csv-keys)
          (download-csv! filename))
     {})))


(comment
  (download-csv "testcsv.csv" "a,b,c\nhello,world,clojure")

  (into [revenue-csv-keys] [[:a :b :c]])




  )
