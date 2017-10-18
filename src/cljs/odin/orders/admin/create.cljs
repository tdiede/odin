(ns odin.orders.admin.create
  (:require [antizer.reagent :as ant]
            [re-frame.core :as rf :refer [subscribe dispatch
                                          reg-event-fx reg-event-db path
                                          reg-sub]]
            [reagent.core :as r]
            [toolbelt.core :as tb]
            [clojure.string :as string]
            [odin.components.service :as service]
            [odin.utils.formatters :as format]))


;; =============================================================================
;; DB
;; =============================================================================


(def default-db
  {:accounts []
   :services []
   :form     {:quantity 1}})


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


(reg-sub
 ::selected-service
 :<- [::services]
 :<- [::form :service]
 (fn [[services service-id] _]
   (tb/find-by #(= service-id (:id %)) services)))


(reg-sub
 ::service-fields
 :<- [::selected-service]
 :<- [::form]
 (fn [[{:keys [price variants] :as service} {:keys [quantity] :as form}]]
   (cond-> []
     (or (some? price) (not (empty? variants)))
     (conj (service/quantity-field :quantity quantity))

     (and (nil? price) (empty? variants))
     (conj (service/notes-field :notes ""))

     (empty? variants)
     (conj (service/price-field :price (or (:price form) price))))))


(reg-sub
 ::can-create?
 :<- [::form]
 (fn [{:keys [service account]} _]
   (and (some? service) (some? account))))


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
                            [:services [:id :code :name :desc :price :billed
                                        [:variants [:id :name :price]]]]]
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


(reg-event-db
 ::clear-service
 [(path ::path)]
 (fn [db _]
   (-> (update-in db [:form] dissoc :service :price :notes)
       (assoc-in [:form :quantity] 1))))


(reg-event-fx
 ::create!
 [(path ::path)]
 (fn [{db :db} [k on-create]]
   {:dispatch [:loading k true]
    :graphql  {:mutation   [[:create_order {:params (:form db)} [:id]]]
               :on-success [::create-success k on-create]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::create-success
 (fn [_ [_ k on-create response]]
   {:dispatch-n (tb/conj-when [[:loading k false]
                               [:modal/hide ::modal]]
                              on-create)}))


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
      :default-value     (str @account)
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-change         #(when (nil? %) (dispatch [::update :account nil]))
      :on-select         #(dispatch [::update :account (tb/str->int %)])}
     (doall (map account-option @accounts))]))


(defn- service-option [{:keys [id code name desc price] :as service}]
  [autocomplete-option {:key   id
                        :label (string/replace name "&amp;" "&")
                        :qterm (str code " " name " " desc)}
   [:span
    [:span {:dangerouslySetInnerHTML {:__html (or name code)}}]
    " - "
    (service/price-text service)]
   [:p.fs2 [:small desc]]])


(defn- service-autocomplete []
  (let [services (subscribe [::services])
        service  (subscribe [::form :service])]
    [ant/auto-complete
     {:style             {:width "100%"}
      :placeholder       "search within service name, code or description"
      :allow-clear       true
      :option-label-prop :label
      :default-value     (str @service)
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (.. opt -props -qterm))]
                             (string/includes? q (string/lower-case val))))
      :on-change         #(when (nil? %) (dispatch [::clear-service]))
      :on-select         #(dispatch [::update :service (tb/str->int %)])}
     (doall (map service-option @services))]))


(defn- order-price
  [{:keys [variants] :as service} {:keys [price variant] :as form}]
  (let [vprice (:price (tb/find-by (comp (partial = variant) :id) variants))
        fprice (when-not (or (zero? price) (string/blank? price)) price)]
    (or vprice fprice (:price service))))


(defn- form []
  (let [account (subscribe [::selected-account])
        service (subscribe [::selected-service])
        fields  (subscribe [::service-fields])
        form    (subscribe [::form])]
    [:div
     [:div.field
      [:label.label "For whom?"]
      [account-autocomplete]]
     [:div.field
      [:label.label "Which service?"]
      [service-autocomplete]]

     (when (and (some? @account) (some? @service))
       [service/card
        {:name     (:name @service)
         :desc     (:desc @service)
         :rental   (:rental @service)
         :quantity (:quantity @form)
         :price    (order-price @service @form)
         :billed   (:billed @service)
         :service  (:id @service)
         :selected true
         :variants (:variants @service)
         :fields   @fields}
        {:on-change (fn [[_ k v]] (dispatch [::update k v]))
         :on-delete #(dispatch [::clear-service])}])]))


(defn- modal [on-create]
  (let [is-showing  (subscribe [:modal/visible? ::modal])
        is-loading  (subscribe [:loading? ::fetch])
        can-create  (subscribe [::can-create?])
        is-creating (subscribe [:loading? ::create!])]
    [ant/modal
     {:title     "Create New Order"
      :width     640
      :visible   @is-showing
      :on-cancel #(dispatch [:modal/hide ::modal])
      :footer    [(r/as-element
                   ^{:key 1}
                   [ant/button
                    {:on-click #(dispatch [:modal/hide ::modal])
                     :size     :large}
                    "Cancel"])
                  (r/as-element
                   ^{:key 2}
                   [ant/button
                    {:disabled (not @can-create)
                     :size     :large
                     :type     :primary
                     :loading  @is-creating
                     :on-click #(dispatch [::create! on-create])}
                    "Create"])]}
     [:div
      (if @is-loading
        [:div {:style {:text-align "center"
                       :padding    "50px 50px"}}
         [ant/spin {:size :large}]]
        [form])]]))


(defn button []
  (r/create-class
   {:component-will-mount
    (fn [_]
      (rf/dispatch-sync [::bootstrap]))
    :reagent-render
    (fn []
      (let [props (r/props (r/current-component))]
        [:div {:style {:display "inline"}}
         [modal (:on-create props)]
         [ant/button
          {:type     :primary
           :icon     "plus"
           :on-click #(dispatch [:modal/show ::modal])}
          "Create Order"]]))}))
