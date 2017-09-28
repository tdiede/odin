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
  {:accounts []
   :services []
   :form     {}})


;; =============================================================================
;; Subscriptions
;; =============================================================================


(reg-sub
 ::create
 (fn [db _]
   (get db ::path)))


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


(reg-sub
 ::form
 :<- [::create]
 (fn [db [_ k]]
   (if (some? k)
     (get-in db [:form k])
     (:form db))))


(reg-sub
 ::selected-account
 :<- [::accounts]
 :<- [::form :account]
 (fn [[accounts account-id] _]
   (tb/find-by #(= account-id (:id %)) accounts)))


;; =============================================================================
;; Events
;; =============================================================================


(reg-event-fx
 ::bootstrap
 (fn [{db :db} _]
   (let [bootstrapped (some? (::path db))]
     {:db       (if bootstrapped db (assoc db ::path default-db))
      :dispatch [::fetch-autocomplete-data bootstrapped]})))


(reg-event-fx
 ::fetch-autocomplete-data
 [(path ::path)]
 (fn [{db :db} [_ bootstrapped]]
   (tb/assoc-when
    {:graphql {:query      [[:accounts [:id :name :email [:property [:name]]]]
                            [:services [:id :code :name :desc :price :billed]]]
               :on-success [::fetch-success]
               :on-failure [:graphql/failure ::fetch]}}
    :dispatch (when-not bootstrapped [:loading ::fetch true]))))


(reg-event-fx
 ::fetch-success
 [(path ::path)]
 (fn [{db :db} [_ response]]
   (let [{:keys [accounts services]} (:data response)]
     {:db       (assoc db :accounts accounts :services services)
      :dispatch [:loading ::fetch false]})))


(reg-event-db
 ::update
 [(path ::path)]
 (fn [db [_ k v]]
   (assoc-in db [:form k] v)))


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
  (let [accounts (subscribe [::accounts])
        account  (subscribe [::form :account])]
    [ant/auto-complete
     {:style             {:width "100%"}
      :placeholder       "search by name or email"
      :allow-clear       true
      :option-label-prop :label
      :value             (str @account)
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-change         #(when (nil? %) (dispatch [::update :account nil]))
      :on-select         #(dispatch [::update :account (tb/str->int %)])}
     (doall (map account-option @accounts))]))


(defn- service-option [{:keys [id code name desc price]}]
  [autocomplete-option {:key   id
                        :label (string/replace name "&amp;" "&")
                        :qterm (str code " " name " " desc)}
   [:span
    [:span {:dangerouslySetInnerHTML {:__html (or name code)}}]
    " - "
    (if-let [p price]
      (format/currency p)
      " quote")]
   [:p [:small desc]]])


(defn- service-autocomplete []
  (let [services (subscribe [::services])
        service  (subscribe [::form :service])]
    [ant/auto-complete
     {:style             {:width "100%"}
      :placeholder       "search within service name, code or description"
      :allow-clear       true
      :option-label-prop :label
      :value             (str @service)
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-change         #(when (nil? %) (dispatch [::update :service nil]))
      :on-select         #(dispatch [::update :service (tb/str->int %)])}
     (doall (map service-option @services))]))


(defn- form []
  (let [form (subscribe [::form])]
    (fn []
      (let [{:keys [account service]} @form]
        [:div
         [:div.field
          [:label.label "For whom?"]
          [account-autocomplete]]
         [:div.field
          [:label.label "Which service?"]
          [service-autocomplete]]

         (when (and account service)
           [:div.box.mt4
            {:style {:min-height 100}}])]))))


(defn- modal []
  (let [is-showing (subscribe [:modal/visible? ::modal])
        is-loading (subscribe [:loading? ::fetch])]
    [ant/modal
     {:title     "Create New Order"
      :visible   @is-showing
      :on-cancel #(dispatch [:modal/hide ::modal])}
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
           :on-click #(dispatch [:modal/show ::modal])}
          "Create Order"]])})))
