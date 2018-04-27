(ns member.services.views
  (:require [antizer.reagent :as ant]
            [iface.components.form :as form]
            [iface.components.services :as services]
            [iface.components.typography :as typography]
            [iface.utils.formatters :as format]
            [member.content :as content]
            [member.routes :as routes]
            [member.services.db :as db]
            [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe]]
            [taoensso.timbre :as timbre]
            [toolbelt.core :as tb]))


(defn menu []
  (let [section (subscribe [:services/section])
        count (subscribe [:services.cart/item-count])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@section]
               :on-click      #(dispatch [:services.section/select
                                          (aget % "key")])}
     [ant/menu-item {:key "book"
                     :class "book-services"}
      "Order services"]
     [ant/menu-item {:key "cart"}
      [ant/icon {:type "shopping-cart"}] "Shopping Cart (" @count ")"]
     [ant/menu-item {:key "history" :style {:float "right"}} "Order history"]
     [ant/menu-item {:key "subscriptions" :style {:float "right"}} "Subscriptions"]
     [ant/menu-item {:key "active-orders" :style {:float "right"}} "Active orders"]]))


(defn format-price
  "Accepts a price and billed status and returns a string with the correct price"
  [price billed]
  (str (if (some? price)
         (format/currency price)
         (format/currency 0))
       (when (= billed :monthly)
         "/mo")))


(defn empty-view [header route anchor message]
  [:div.empty-cart
   [:p.fs3.bold header]
   [:p.fs3 "Go to "
    [:a {:href route} anchor] message]])


;; ==============================================================================
;; BOOK SERVICES ================================================================
;; ==============================================================================


(defn category-icon [{:keys [category label]}]
  (let [selected (subscribe [:services.book/category])
        route    (subscribe [:services.book.category/route category])
        icon     (subscribe [:services.book/category-icon category])]
    [:div.category-icon.column
     {:class (when (= category @selected) "is-active")}
     [:a {:href @route}
      [:img {:src @icon}]
      [:p label]]]))


(defn categories []
  (let [categories (subscribe [:services.book/categories])]
    [:div.container.catalogue-menu
     [:div.columns
      (doall
       (map-indexed
        #(with-meta [category-icon %2] {:key %1})
        @categories))]]))


(defn service-item [{:keys [name description price billed updated_at] :as service}]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-3
      [:h4.subtitle.is-5 name]]
     [:div.column.is-6
      [:p.fs3 description]]
     [:div.column.is-1
      [:p.price (format-price price billed)]]
     [:div.column.is-2
      [ant/button
       {:type     "primary"
        :on-click #(dispatch [:services.add-service/show service])}
       "Request Service"]]]]])


(defn catalog [services]
  (let [selected @(subscribe [:services.book/category])]
    [:div.catalogue
     [:div.colums {:style {:margin-bottom "0px"}}
      [:div.colums.is-10
       [:h3.title.is-4 {:style {:margin-bottom "10px"}}
        (clojure.string/capitalize (name selected))]]
      (doall
       (map-indexed #(with-meta [service-item %2] {:key %1}) services))]]))


(defn shopping-cart-button []
  (let [item-count (subscribe [:services.cart/item-count])
        total-cost (subscribe [:services.cart/total-cost])]
    [ant/affix {:offsetBottom 20}
     [:div.has-text-right
      [ant/button
       {:size :large
        :type :primary
        :class "ant-btn-xl"
        :on-click #(dispatch [:services.section/select "cart"])}
       "Checkout - $" @total-cost " (" @item-count ")"]]]))


;; ==============================================================================
;; service/order fields =========================================================
;; ==============================================================================

;; TODO make it look pretty?

(defmulti field-value (fn [k value options] k))


(defmethod field-value :time [k value options]
  [:span
   [:p.fs3 (format/time-short value)]])


(defmethod field-value :date [k value options]
  [:span
   [:p.fs3 (format/date-short value)]])


(defmethod field-value :variants [k value options]
  (let [vlabel (reduce (fn [v option] (if (= (keyword value) (:key option)) (:label option) v)) nil options)]
    [:span
     [:p.fs3 vlabel]]))


(defmethod field-value :text [k value options]
  [:span
   [:p.fs3 value]])


(defmethod field-value :number [k value options]
  [:span
   [:p.fs3 value]])


(defmethod field-value :dropdown [k value options]
  [:span
   [:p.fs3 value]])


(defn fields-data-row [fields]
  [:div.columns
   (map-indexed
    (fn [i {:keys [label value type options] :as field}]
      ^{:key i}
      [:div.column.is-half.cart-item-info
       [:span
        [:p.fs3.bold label]]
       [field-value (keyword (name type)) value options]])
    fields)])


(defn fields-data [fields]
  [:div.cart-item
   (map-indexed
    (fn [i field-pair]
      ^{:key i}
      [fields-data-row field-pair])
    (partition 2 2 nil fields))])


;; ==============================================================================
;; shopping cart ================================================================
;; ==============================================================================


(defn cart-item [{:keys [index name description price fields fee? billed]}]
  (let [service-item {:index       index
                      :name        name
                      :description description
                      :price       price}]
    [ant/card {:style {:margin "10px 0"}}
     [:div.columns
      [:div.column.is-6
       [:h4.subtitle.is-5 name]
       (when fee? [:div description])]
      [:div.column.is-2
       [:p.price (format-price price billed)]]
      (when (not fee?)
        [:div.column.is-2.align-right
         [ant/button {:icon     "edit"
                      :on-click #(dispatch [:services.cart.item/edit service-item fields])}
          "Edit Item"]])
      (when (not fee?)
        [:div.column.is-2
         [ant/button
          {:type     "danger"
           :icon     "close"
           :on-click #(dispatch [:services.cart.item/remove index name])}
          "Remove item"]])]
     (when-not (empty? fields)
       [fields-data (sort-by :index fields)])]))


(defn shopping-cart-footer [requester]
  (let [has-card   (subscribe [:payment-sources/has-card? (:id requester)])
        submitting (subscribe [:ui/loading? :services.cart/submit])]
    [:div.cart-footer.has-text-right
     [:p
      [:b "NOTE: "] "Service requests are treated as individual billable items. You will be charged for each service as it is fulfilled."]
     [ant/button {:class    "ant-btn-xl"
                  :type     "primary"
                  :on-click #(if-not @has-card
                               (dispatch [:modal/show :payment.source/add])
                               (dispatch [:services.cart/submit requester]))
                  :loading  @submitting}
      "Order now"]]))


(defn- filter-svcs-with-fees
  [cart-items]
  (filter #(not (empty? (:fees %))) cart-items))


(defn- extract-fee-ids
  [services]
  (reduce
   (fn [fees svc]
     (apply conj fees (map :id (:fees svc))))
   #{}
   services))


(defn- build-fees-map
  [fee-ids]
  (reduce
   (fn [fee id]
     (assoc fee id {}))
   {}
   fee-ids))


(defn- update-fees-map
  [fees-acc {:keys [fees] :as svc}]
  (reduce
   (fn [fees-acc {:keys [id name price] :as fee}]
     (if (empty? (get-in fees-acc [id]))
       (assoc-in fees-acc [id] {:fee? true :id id :name name :price price :description (str "For "  (:name svc))})
       (-> (update-in fees-acc [id :price] #(+ (get-in fees-acc [id :price]) (/ price 2)))
           (update-in [id :description] str (str ", " (:name svc))))))
   fees-acc
   fees))


(defn- extract-fees
  [cart-items]
  (let [services (filter #(not (empty? (:fees %))) cart-items)
        fees (->> (extract-fee-ids services)
                  build-fees-map)]
    (vals (reduce update-fees-map fees services))))


(defn shopping-cart-body [cart-items requester]
  [:div
   (doall
    (map-indexed
     #(with-meta [cart-item %2] {:key %1})
     (concat cart-items (extract-fees cart-items))))
   [shopping-cart-footer requester]])


;; add credit card modal ========================================================


(defn modal-add-credit-card []
  (let [is-visible (subscribe [:modal/visible? :payment.source/add])]
    (r/create-class
     {:component-will-mount
      (fn [_]
        (dispatch [:stripe/load-scripts "v2"])
        (dispatch [:stripe/load-scripts "v3"]))
      :reagent-render
      (fn []
        [ant/modal
         {:title     "Add credit card"
          :width     640
          :visible   @is-visible
          :on-ok     #(dispatch [:modal/hide :payment.souce/add])
          :on-cancel #(dispatch [:modal/hide :payment.source/add])
          :footer    nil}
         [:p "The credit card you provide will be kept on file."]
         [:br]
         [:div
          (r/as-element (ant/create-form
                         (form/credit-card
                          {:is-submitting @(subscribe [:ui/loading? :services.cart.add.card/save-stripe-token!])
                           :on-add-card   #(dispatch [:services.cart.add.card/save-stripe-token! %])
                           :on-click      #(dispatch [:modal/hide :payment.source/add])})))]])})))


;; ==============================================================================
;; orders generic ===============================================================
;; ==============================================================================


(defn orders-header [type]
  [:div.columns {:style {:padding        "0 1.5rem"
                         :margin-bottom  "0"
                         :text-transform "uppercase"}}
   [:div.column.is-6
    [:h4.subtitle.is-6.bold (str "Requested " type)]]
   [:div.column.is-2
    [:h4.subtitle.is-6.bold "Request date"]]
   [:div.column.is-1
    [:h4.subtitle.is-6.bold "Price"]]
   [:div.column.is-3
    [:h4.subtitle.is-6.bold "Status"]]])


(defn status-tag [status]
  (case status
    :placed     [:span.tag.is-placed (name status)]
    :fulfilled  [:span.tag.is-fulfilled (name status)]
    :processing [:span.tag.is-processing (name status)]
    :failed     [:span.tag.is-processing "processing"]
    :charged    [:span.tag.is-success (name status)]
    :canceled   [:span.tag.is-cancel (name status)]
    [:span.tag.is-hollow (name status)]))


(defn- cancel-order-button
  [{:keys [id type]} account-id loading canceling]
  (let [opts {:type     "danger"
              :icon     "close"
              :loading  (if (= @canceling id) @loading false)}]
    (if (= type :fee)
      [ant/tooltip
       {:placement "top"
        :title "For help with this order, reach out to a community team member."}
       [ant/button (assoc opts :disabled true) "Cancel"]]
      [ant/popconfirm
       {:title "Are you sure you want to cancel this order?"
        :ok-text "Yes, cancel order"
        :cancel-text "No"
        :on-confirm #(dispatch [:services.order/cancel-order id account-id])}
       [ant/button opts "Cancel"]])))


(defn above-the-fold [{:keys [id name date price status billed type cancel-btn] :as order} is-open requester]
  (let [loading    (subscribe [:ui/loading? :services.order/cancel-order])
        account-id (:id requester)
        canceling  (subscribe [:orders/canceling])]
    [:div.columns
     [:div.column.is-6
      [:span [ant/button {:on-click #(swap! is-open not)
                          :icon     (if @is-open "minus" "plus")
                          :style    {:width        "30px"
                                     :align        "center"
                                     :padding      "0px"
                                     :font-size    20
                                     :margin-right "10px"}}]]
      [:span {:style {:display "inline-block"}}
       [:p.body name]]]
     [:div.column.is-2
      [:p.body (format/date-short date)]]
     [:div.column.is-1
      [:p.body (format-price price billed)]]
     [:div.column.is-1
      [status-tag status]]
     [:div.column.is-2.has-text-right
      (when (and cancel-btn (or (= status :pending) (= billed :monthly)))
        [ant/tooltip
         [cancel-order-button order account-id loading canceling]])]]))


(defn paginated-list [items requester list-item-fn]
  (let [state (r/atom {:current 1})]
    (fn [items]
      (let [{:keys [current]} @state
            items'            (->> (drop (* (dec current) 10) items)
                                   (take (* current 10)))]
        [:div
         (map
          #(with-meta [list-item-fn % requester] {:key (:id %)})
          items')
         (when (> (count items) 10)
           [ant/pagination
            {:style     {:margin-top "20px"}
             :current   current
             :total     (count items)
             :on-change #(swap! state assoc :current %)}])]))))


;; ==============================================================================
;; active orders content ========================================================
;; ==============================================================================


(defn active-orders-header []
  [orders-header "order"])


(defn active-order-item [{:keys [fields] :as order} requester]
  (let [is-open (r/atom false)
        order'  (assoc order :date (:created order) :cancel-btn true)]
    (fn []
      [ant/card
       (r/as-element [above-the-fold order' is-open requester])
       (when @is-open
         [fields-data (sort-by :index fields)])])))


(defn active-orders-list [orders requester]
  [paginated-list (sort-by :created > orders) requester active-order-item])


(defn active-orders [orders requester]
  [:div
   [active-orders-header]
   [active-orders-list orders requester]])


;; ==============================================================================
;; manage subscriptions =========================================================
;; ==============================================================================


(defn manage-subscriptions-header []
  [orders-header "subscription"])


(defn subscription-details [fields payments]
  [:div
   [fields-data fields]
   [:br]
   [:p.fs3 "Go to "
    [:a {:href (routes/path-for :profile.payment/history)} "Payments History"] " to see payments made for this service"]])


;; TODO need to actually test this...
;; subscriptions are not getting their payments created
(defn get-payment-status [payment]
  (let [status (:status payment)]
    (case status
      :due     :processing
      :paid    :charged
      :pending :processing
      :failed  :processing
      status)))


(defn active-subscription-item
  [{:keys [payments fields] :as subscription} requester]
  (let [is-open       (r/atom false)
        status        (if (not (empty? payments))
                        (get-payment-status (first (sort-by :created > payments)))
                        (:status subscription))
        subscription' (assoc subscription :date (:created subscription) :status status :cancel-btn true)]
    (fn []
      [ant/card
       (r/as-element [above-the-fold subscription' is-open requester])
       (when @is-open
         [subscription-details (sort-by :index fields) (sort-by :created > payments)])])))


(defn active-subscriptions-list [subscriptions requester]
  [paginated-list (sort-by :created > subscriptions) requester active-subscription-item])


(defn active-subscriptions [subscriptions requester]
  [:div
   [manage-subscriptions-header]
   [active-subscriptions-list subscriptions requester]])


;; ==============================================================================
;; order history ================================================================
;; ==============================================================================


(defn order-history-header []
  [:div.columns {:style {:padding        "0 1.5rem"
                         :margin-bottom  "0"
                         :text-transform "uppercase"}}
   [:div.column.is-6
    [:h4.subtitle.is-6.bold "Requested order"]]
   [:div.column.is-2
    [:h4.subtitle.is-6.bold "Updated on"]]
   [:div.column.is-1
    [:h4.subtitle.is-6.bold "Price"]]
   [:div.column.is-3
    [:h4.subtitle.is-6.bold "Status"]]])


;; is it useful to have more payment information?
;; TODO gather feedback on completed services?
(defn order-history-item
  [{:keys [name price status fields updated payments] :as order} requester]
  (let [is-open (r/atom false)
        order'  (assoc order :date (:updated order) :cancel-btn false)]
    (fn []
      [ant/card
       (r/as-element [above-the-fold order' is-open requester])
       (when @is-open
         [subscription-details (sort-by :index fields) (sort-by :created > payments)]
         #_[fields-data (sort-by :index fields)])])))


(defn order-history-list [history requester]
  [paginated-list (sort-by :updated > history) requester order-history-item])


(defn order-history [history requester]
  [:div
   [order-history-header]
   [order-history-list history requester]])


;; ==============================================================================
;; premium services content =====================================================
;; ==============================================================================


(defmulti content :page)


(defmethod content :services/book [_]
  (let [selected (subscribe [:services.book/category])
        services (subscribe [:services.book/services-by-catalog @selected])]
    [:div
     [services/service-modal
      {:action      "Add"
       :is-visible  @(subscribe [:services.add-service/visible?])
       :service     @(subscribe [:services.add-service/adding])
       :form-fields @(subscribe [:services.add-service/form])
       :can-submit  @(subscribe [:services.add-service/can-submit?])
       :on-cancel   #(dispatch [:services.add-service/close])
       :on-submit   #(dispatch [:services.add-service/add])
       :on-change   #(dispatch [:services.add-service.form/update %1 %2])}]
     [categories]
     [catalog @services]
     [shopping-cart-button]]))


(defmethod content :services/active-orders [{:keys [requester]}]
  (let [orders (subscribe [:orders/active])]
    [:div
     (if-not (empty? @orders)
       [active-orders (sort-by :created > @orders) requester]
       [empty-view "You don't have any active orders at the moment" (routes/path-for :services/book) "Order services" " to request services"])]))


(defmethod content :services/subscriptions [{:keys [requester]}]
  (let [subscriptions (subscribe [:orders/subscriptions])]
    [:div
     (if-not (empty? @subscriptions)
       [active-subscriptions @subscriptions requester]
       [empty-view "You don't have any active subscriptions" (routes/path-for :services/book) "Order services" " to request services"])]))


(defmethod content :services/history [{:keys [requester]}]
  (let [history (subscribe [:orders/history])]
    [:div
     (if-not (empty? @history)
       [order-history @history requester]
       [empty-view "You don't have an order history yet" (routes/path-for :services/book) "Order services" " to add services to your requests"])]))



(defmethod content :services/cart [{:keys [requester] :as route}]
  (let [cart-items (subscribe [:services.cart/cart])]
    [:div
     [modal-add-credit-card]
     [services/service-modal
      {:action      "Edit"
       :is-visible  @(subscribe [:services.add-service/visible?])
       :service     @(subscribe [:services.add-service/adding])
       :form-fields @(subscribe [:services.add-service/form])
       :can-submit  @(subscribe [:services.add-service/can-submit?])
       :on-cancel   #(dispatch [:services.add-service/close])
       :on-submit   #(dispatch [:services.cart.item/save-edit])
       :on-change   #(dispatch [:services.add-service.form/update %1 %2])}]
     (if-not (empty? @cart-items)
       [shopping-cart-body (sort-by :index @cart-items) requester]
       [empty-view "There are no services selected" (routes/path-for :services/book) "Order services" " to add services to your requests"])]))



(defmethod content/view :services [route]
  (let [header  (subscribe [:services/header])
        subhead (subscribe [:services/subhead])]
    [:div
     (typography/view-header @header @subhead)
     [:div.mb3
      [menu]]
     (content route)]))
