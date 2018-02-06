(ns admin.orders.create
  (:require [antizer.reagent :as ant]
            [clojure.string :as string]
            [iface.components.order :as order]
            [iface.components.service :as service]
            [iface.utils.formatters :as format]
            [reagent.core :as r]
            [re-frame.core :as rf :refer [subscribe dispatch
                                          reg-event-fx reg-event-db path
                                          reg-sub]]
            [toolbelt.core :as tb]))

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
   (->> (:services db)
        (sort-by :name))))


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
   (cond-> [(service/notes-field :notes "")]
     (or (some? price) (not (empty? variants)))
     (conj (service/quantity-field :quantity quantity))

     ;; (and (nil? price) (empty? variants))
     ;; (conj (service/notes-field :notes ""))

     (empty? variants)
     (conj (service/price-field :price (or (:price form) price))))))


(reg-sub
 ::line-items-valid
 :<- [::form :line_items]
 (fn [items _]
   (order/line-items-valid? items)))


(reg-sub
 ::can-create?
 :<- [::form]
 :<- [::line-items-valid]
 (fn [[{:keys [service account]} lines-valid] _]
   (and (some? service)
        (some? account)
        lines-valid)))


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
    {:graphql {:query      [[:accounts {:params {:roles [:member]}} [:id :name :email [:property [:name]]]]
                            [:services [:id :code :name :desc :price :billed
                                        [:variants [:id :name :price]]]]]
               :on-success [::fetch-success]
               :on-failure [:graphql/failure ::fetch]}}
    :dispatch (when-not bootstrapped [:ui/loading ::fetch true]))))


(reg-event-fx
 ::fetch-success
 [(path ::path)]
 (fn [{db :db} [_ response]]
   (let [{:keys [accounts services]} (:data response)]
     {:db       (assoc db :accounts accounts :services services)
      :dispatch [:ui/loading ::fetch false]})))


(reg-event-fx
 ::update
 [(path ::path)]
 (fn [{db :db} [_ k v]]
   (let [variant (when (= k :service)
                   (when-let [v (-> (tb/find-by #(= (:id %) v) (:services db))
                                    :variants
                                    first)]
                     (:id v)))]
     (tb/assoc-when
      {:db (assoc-in db [:form k] v)}
      :dispatch (when-some [v variant] [::update :variant v])))))


(reg-event-db
 ::clear-service
 [(path ::path)]
 (fn [db _]
   (-> (update-in db [:form] dissoc :service :price :summary :request :cost :variant :line_items)
       (assoc-in [:form :quantity] 1))))


(reg-event-db
 ::clear-form
 [(path ::path)]
 (fn [db _]
   (assoc db :form {:quantity 1})))


(reg-event-fx
 ::create!
 [(path ::path)]
 (fn [{db :db} [k on-create]]
   (let [form (:form db)]
     {:dispatch-n [[:ui/loading k true] [::clear-form]]
      :graphql    {:mutation   [[:create_order {:params form} [:id]]]
                   :on-success [::create-success k on-create]
                   :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::create-success
 (fn [_ [_ k on-create response]]
   {:dispatch-n (tb/conj-when [[:ui/loading k false]
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


(defn- get-qterm [opt]
  (goog.object/getValueByKeys opt "props" "qterm"))


(defn- account-autocomplete []
  (let [accounts (subscribe [::accounts])
        account  (subscribe [::form :account])]
    (fn []
      [ant/auto-complete
       {:style             {:width "100%"}
        :placeholder       "search by name or email"
        :allow-clear       true
        :option-label-prop :label
        :value             (str @account)
        :filter-option     (fn [val opt]
                             (let [q (string/lower-case (get-qterm opt))]
                               (string/includes? q (string/lower-case val))))
        :on-change         #(when (nil? %) (dispatch [::update :account nil]))
        :on-select         #(dispatch [::update :account (tb/str->int %)])}
       (doall (map account-option @accounts))])))


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
      :value             (str @service)
      :filter-option     (fn [val opt]
                           (let [q (string/lower-case (get-qterm opt))]
                             (string/includes? q (string/lower-case val))))
      :on-change         #(when (nil? %) (dispatch [::clear-service]))
      :on-select         #(dispatch [::update :service (tb/str->int %)])}
     (doall (map service-option @services))]))


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
       [ant/card {:class "svc" :bodyStyle {:padding "10px 16px"}}
        [order/form
         @service
         {:cost       (:cost @form)
          :price      (:price @form)
          :billed     (:billed @service)
          :variant    (:variant @form)
          :quantity   (:quantity @form)
          :request    (:request @form)
          :summary    (:summary @form)
          :line_items (:line_items @form)}
         {:on-change (fn [k v] (dispatch [::update k v]))}]])]))


(defn- modal [on-create]
  (let [is-showing  (subscribe [:modal/visible? ::modal])
        is-loading  (subscribe [:ui/loading? ::fetch])
        can-create  (subscribe [::can-create?])
        is-creating (subscribe [:ui/loading? ::create!])]
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
