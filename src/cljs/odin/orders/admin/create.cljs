(ns odin.orders.admin.create
  (:require [antizer.reagent :as ant]
            [re-frame.core :as rf :refer [subscribe dispatch
                                          reg-event-fx reg-event-db path
                                          reg-sub]]
            [reagent.core :as r]
            [toolbelt.core :as tb]
            [clojure.string :as string]
            [odin.utils.formatters :as format]))


;; =============================================================================
;; DB
;; =============================================================================


(def default-db
  {:showing  true
   :accounts []
   :services []})


;; =============================================================================
;; Subscriptions
;; =============================================================================


(reg-sub
 ::create
 (fn [db _]
   (get db ::path)))


(reg-sub
 ::showing?
 :<- [::create]
 (fn [db _]
   (:showing db)))


(reg-sub
 ::accounts
 :<- [::create]
 (fn [db _]
   (:accounts db)))


(reg-sub
 ::services
 :<- [::create]
 (fn [db _]
   (:services db)))


;; =============================================================================
;; Events
;; =============================================================================


(reg-event-fx
 ::bootstrap
 (fn [{db :db} _]
   {:db       (assoc db ::path default-db)
    :dispatch [::fetch-autocomplete-data]}))


(reg-event-fx
 ::fetch-autocomplete-data
 [(path ::path)]
 (fn [{db :db} _]
   {:dispatch [:loading ::fetch true]
    :graphql  {:query      [[:accounts [:id :name :email [:property [:name]]]]
                            [:services [:id :code :name :desc :price :billed]]]
               :on-success [::fetch-success]
               :on-failure [:graphql/failure ::fetch]}}))


(reg-event-fx
 ::fetch-success
 [(path ::path)]
 (fn [{db :db} [_ response]]
   (let [{:keys [accounts services]} (:data response)]
     {:db       (assoc db :accounts accounts :services services)
      :dispatch [:loading ::fetch false]})))


(reg-event-db
 ::showing
 [(path ::path)]
 (fn [db [_ showing]]
   (assoc db :showing showing)))


;; =============================================================================
;; Views
;; =============================================================================


(def autocomplete-option
  (r/adapt-react-class (.. js/antd -AutoComplete -Option)))


(defn- account-option [{:keys [id name email property]}]
  [autocomplete-option {:key   id
                        :label name
                        :qterm (str name " " email)}
   name
   (if-let [pname (:name property)]
     (str " (" email ") - " pname)
     (str " (" email ")"))])


(defn- account-autocomplete []
  (let [accounts (subscribe [::accounts])]
    [ant/auto-complete
     {:style             {:width "100%"}
      :placeholder       "search by name or email"
      :allow-clear       true
      :option-label-prop :label
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-select         #(dispatch [::select-account (tb/str->int %)])}
     (doall (map account-option @accounts))]))


(defn- service-option [{:keys [id code name desc price]}]
  [autocomplete-option {:key   id
                        :label name
                        :qterm (str code " " name " " desc)}
   [:span
    (or name code)
    " - "
    (if-let [p price]
      (format/currency p)
      " quote")]
   [:p [:small desc]]])


(defn- service-autocomplete []
  (let [services (subscribe [::services])]
    [ant/auto-complete
     {:style             {:width "100%"}
      :placeholder       "search within service name, code or description"
      :allow-clear       true
      :option-label-prop :label
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-select         #(dispatch [::select-service (tb/str->int %)])}
     (doall (map service-option @services))]))


(defn- form []
  [:div
   [:div.field
    [:label.label "For whom?"]
    [account-autocomplete]]
   [:div.field
    [:label.label "Which service?"]
    [service-autocomplete]]])


(defn- modal []
  (let [is-showing (subscribe [::showing?])
        is-loading (subscribe [:loading? ::fetch])]
    [ant/modal
     {:title     "Create New Order"
      :visible   @is-showing
      :on-cancel #(dispatch [::showing false])}
     [:div
      (if @is-loading
        [:div {:style {:text-align "center"
                       :padding    "50px 50px"}}
         [ant/spin {:size :large}]]
        [form])]]))


;; What do we need to know to creat an order?
;; - account
;; - service
;; - quantity
;; - description
;; - price (pre-populated from service)
;; - variant?


(defn button []
  (let []
    (r/create-class
     {:component-will-mount
      (fn [_]
        (rf/dispatch-sync [::bootstrap]))
      :reagent-render
      (fn []
        [:div
         [modal]
         [ant/button
          {:type     :primary
           :icon     "plus"
           :on-click #(dispatch [::showing true])}
          "Create Order"]])})))
