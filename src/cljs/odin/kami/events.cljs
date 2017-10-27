(ns odin.kami.events
  (:require [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [odin.kami.db :as db]
            [odin.routes :as routes]
            [ajax.core :as ajax]
            [toolbelt.core :as tb]))


(defmethod routes/dispatches :kami
  [{:keys [params] :as route}]
  (let [{:keys [q addr]} params]
    (cond-> []
      (some? q)    (conj [:kami/search q])
      (some? addr) (conj [:kami/score addr]))))


(reg-event-fx
 :kami/query
 [(path db/path)]
 (fn [_ [_ q]]
   {:route (routes/path-for :kami :query-params {:q q})}))


(reg-event-fx
 :kami/search
 [(path db/path)]
 (fn [{db :db} [k q]]
   (when-not (= (:query db) q)
     {:dispatch   [:loading :kami/search true]
      :db         (assoc db :query q)
      :http-xhrio {:uri             "/api/kami/search"
                   :method          :get
                   :params          {:q q}
                   :response-format (ajax/transit-response-format)
                   :on-success      [::search-success k]
                   :on-failure      [::search-failure k]}})))


(reg-event-fx
 ::search-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db       (assoc db :addresses (get-in response [:data :addresses]))
    :dispatch [:loading k false]}))


(reg-event-fx
 ::search-failure
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (tb/error k response)
   {:dispatch [:loading k false]}))


(reg-event-fx
 :kami/score
 [(path db/path)]
 (fn [{db :db} [k addr]]
   (let [address (tb/find-by #(= addr (str (:eas_baseid %))) (:addresses db))]
     {:dispatch   [:loading :kami/score true]
      :db         (assoc db :selected-address address)
      :http-xhrio {:uri             "/api/kami/score"
                   :method          :get
                   :params          {:addr addr}
                   :response-format (ajax/transit-response-format)
                   :on-success      [::score-success k]
                   :on-failure      [::score-failure k]}})))


(reg-event-fx
 ::score-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   (let [kami (get-in response [:data :kami])
         {datasets :datasets
          criteria :criteria
          scores   :scores} kami])
    ;; (tb/log (keys datasets))
    ;; (tb/log (keys criteria))
    ;; (tb/log (keys scores)))
   {:dispatch [:loading k false]
    :db       (assoc db :report (get-in response [:data :kami]))}))


(reg-event-fx
 ::score-failure
 [(path db/path)]
 (fn [_ [_ k response]]
   (tb/error k response)
   {:dispatch [:loading k false]}))
