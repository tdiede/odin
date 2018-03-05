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
      "Book services"]
     [ant/menu-item {:key "cart"}
      [ant/icon {:type "shopping-cart"}] @count]
     [ant/menu-item {:key "history" :style {:float "right"}} "Order history"]
     [ant/menu-item {:key "subscriptions" :style {:float "right"}} "Subscriptions"]
     [ant/menu-item {:key "active-orders" :style {:float "right"}} "Active orders"]]))


;; ==============================================================================
;; BOOK SERVICES ================================================================
;; ==============================================================================


(defn category-icon [{:keys [category label]}]
  (let [selected (subscribe [:services.book/category])
        route    (subscribe [:services.book.category/route category])]
    [:div.category-icon.column
     {:class (when (= category @selected) "is-active")}
     [:a {:href @route}
      [:img {:src "http://via.placeholder.com/150x150"}]
      [:p label]]]))


(defn categories []
  (let [categories (subscribe [:services.book/categories])]
    [:div.container.catalogue-menu
     [:div.columns
      (doall
       (map-indexed
        #(with-meta [category-icon %2] {:key %1})
        @categories))]]))


(defn catalogue-item [{:keys [service] :as item}]
  [ant/card
   [:div.service
    [:div.columns
     [:div.column.is-3
      [:h4.subtitle.is-5 (:title service)]]
     [:div.column.is-6
      [:p.fs3 (:description service)]]
     [:div.column.is-1
      [:p.price (format/currency (:price service))]]
     [:div.column.is-2
      [ant/button
       {:on-click #(dispatch [:services.add-service/show item])}
       "Request Service"]]]]])


(defn catalogue [{:keys [id name items key] :as c}]
  (let [route    (subscribe [:services.book.category/route key])
        selected (subscribe [:services.book/category])
        has-more (subscribe [:services.book.category/has-more? id])]
    [:div.catalogue
     [:div.columns {:style {:margin-bottom "0px"}}
      [:div.column.is-10
       [:h3.title.is-4 name]]
      (when (and (= @selected :all) @has-more)
        [:div.column.is-2.has-text-right {:style {:display "table"}}
         [:a {:href  @route
              :style {:display        "table-cell"
                      :vertical-align "middle"
                      :padding-top    8}}
          "See More"
          [ant/icon {:style {:margin-left 4} :type "right"}]]])]
     (doall
      (map-indexed #(with-meta [catalogue-item %2] {:key %1}) items))]))


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
;; shopping cart ================================================================
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


(defmethod field-value :desc [k value options]
  [:span
   [:p.fs3 value]])


(defn- column-fields-2 [fields]
  [:div
   (map-indexed
    (fn [i row]
      ^{:key i}
      [:div.columns
       (for [{:keys [id type label value options]} row]
         ^{:key id}
         [:div.column.is-half.cart-item-info
          [:span
           [:p.fs3.bold label]]
          [field-value type value options]])])
    (partition 2 2 nil fields))])


(defn cart-item-data [fields]
  [:div.cart-item
   [column-fields-2 fields]])


(defn cart-item [{:keys [id title description price fields]}]
  (let [service-item {:id          id
                      :title       title
                      :description description
                      :price       price}]
    [ant/card {:style {:margin "10px 0"}}
     [:div.columns
      [:div.column.is-6
       [:h4.subtitle.is-5 title]]
      [:div.column.is-2
       [:p.price (format/currency price)]]
      [:div.column.is-2.align-right
       [ant/button {:icon "edit"
                    :on-click #(dispatch [:services.cart.item/edit service-item fields])}
        "Edit Item"]]
      [:div.column.is-2
       [ant/button
        {:type     "danger"
         :icon     "close"
         :on-click #(dispatch [:services.cart.item/remove id])}
        "Remove item"]]]
     (when-not (empty? fields)
       [cart-item-data (sort-by :index fields)])]))


(defn shopping-cart-footer [requester]
  (let [has-card (subscribe [:payment-sources/has-card? (:id requester)])]
    [:div.cart-footer.has-text-right
     [:p.fs2
      [:b "NOTE: "] "Premium Service requests are treated as individual billable items. You will be charged for each service as it is fulfilled."]
     [ant/button {:class "ant-btn-xl"
                  :type "primary"
                  :on-click (fn []
                              (if-not @has-card
                                (dispatch [:modal/show :payment.source/add])
                                (dispatch [:services.cart/submit])))}
      "Submit orders"]]))


(defn shopping-cart-body [cart-items requester]
  [:div
   (doall
    (map-indexed #(with-meta [cart-item %2] {:key %1}) cart-items))
   [shopping-cart-footer requester]])


(defn empty-cart []
  [:div.empty-cart
   [:p.fs3.bold "There are no premium services selected"]
   [:p.fs3 "Go to "
    [:a {:href "book"} "Book services"] " to add premium services to your requests"]])



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
        [ant/modal {:title     "Add credit card"
                    :width     640
                    :visible   @is-visible
                    :on-ok     #(dispatch [:modal/hide :payment.souce/add])
                    :on-cancel #(dispatch [:modal/hide :payment.source/add])
                    :footer    nil}
         [:div
          (r/as-element (ant/create-form
                         (form/credit-card {:is-submitting @(subscribe [:ui/loading? :services.cart.add.card/save-stripe-token!])
                                            :on-add-card   #(dispatch [:services.cart.add.card/save-stripe-token! %])
                                            :on-click      #(dispatch [:modal/hide :payment.source/add])})))]])})))


;; ==============================================================================
;; active orders content ========================================================
;; ==============================================================================

(defn active-orders-header []
  [:div.columns {:style {:padding       "0 1.5rem"
                         :margin-bottom "0"}}
   [:div.column.is-1]
   [:div.column.is-5
    [:h4.subtitle.is-5.bold "Requested service"]]
   [:div.column.is-2
    [:h4.subtitle.is-5.bold "Request date"]]
   [:div.column.is-1
    [:h4.subtitle.is-5.bold "Price"]]
   [:div.column.is-3
    [:h4.subtitle.is-5.bold "Status"]]])


(defn above-the-fold [{:keys [name request-date price status]} is-open]
  [:div.columns
   [:div.column.is-1
    [ant/button {:on-click #(swap! is-open not)
                 :icon     (if @is-open "minus" "plus")
                 :style    {:width     "30px"
                            :align     "center"
                            :padding   "0px"
                            :font-size 20}}]]
   [:div.column.is-5
    [:p.body name]]
   [:div.column.is-2
    [:p.body (format/date-short request-date)]]
   [:div.column.is-1
    [:p.body (format/currency price)]]
   [:div.column.is-1
    [ant/tag status]]
   [:div.column.is-2.has-text-right
    (when (= status :pending)
      [ant/button {:type "danger"
                   :icon "close"} "Cancel"])]])


;; is it worth writing another 2 column field component just to adjust
;; to the column layout here? Or are we ok splitting it down the middle?
(defn below-the-fold [fields]
  [:div
   [:hr {:style {:margin "0.5rem 0 1.75rem 0"}}]
   [column-fields-2 fields]])


(defn active-order-item []
  (let  [main    {:name         "Dog walking - single"
                  :request-date "2018-02-27T19:15:00.134Z"
                  :price        20
                  :status       :pending}
         fields  [{:id      2
                   :index   2
                   :label   "Dog size:"
                   :type    :variants
                   :value   "m"
                   :options [{:key   :s
                              :label "Small"}
                             {:key   :m
                              :label "Medium"}
                             {:key   :l
                              :label "Large"}]}
                  {:id    0
                   :index 0
                   :type  :date
                   :label "Date:"
                   :value "2018-02-27T19:15:00.134Z"}
                  {:id    1
                   :index 1
                   :type  :time
                   :label "Time:"
                   :value "2018-02-27T19:15:00.134Z"}
                  {:id    3
                   :index 3
                   :type  :desc
                   :label "Additional notes:"
                   :value "Vestibulum convallis, lorem a tempus semper, dui dui euismod elit, vitae placerat urna tortor vitae lacus.  Nullam libero mauris, consequat quis, varius et, dictum id, arcu.  Mauris mollis tincidunt felis.  Aliquam feugiat tellus ut neque.  Nulla facilisis, risus a rhoncus fermentum, tellus tellus lacinia purus, et dictum nunc justo sit amet elit."}]
         is-open (r/atom false)]
    (fn []
      [ant/card
       (r/as-element [above-the-fold main is-open])
       (when @is-open
         [below-the-fold (sort-by :index fields)])])))


;; ==============================================================================
;; premium services content =====================================================
;; ==============================================================================



(defmulti content :page)


(defmethod content :services/book [_]
  (let [selected   (subscribe [:services.book/category])
        catalogues (subscribe [:services.book/catalogues])
        c          (first (filter #(= @selected (:key %)) @catalogues))]
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
     (if (= @selected :all)
       (doall
        (->> (map (fn [c] (update c :items #(take 2 %))) @catalogues)
             (map-indexed #(with-meta [catalogue %2] {:key %1}))))
       [catalogue c])
     [shopping-cart-button]]))


(defmethod content :services/active-orders [_]
  [:div
   [active-orders-header]
   [active-order-item]])


(defmethod content :services/subscriptions [_]
  [:div
   [:h3 "Manage your subscriptions, yo"]])


(defmethod content :services/history [_]
  [:div
   [:h3 "Look at all the things youve ordered, yo"]])



(defmethod content :services/cart [{:keys [requester] :as route}]
  (let [cart-items (subscribe [:services.cart/cart])]
    [:div
     ;; (.log js/console @cart-items)
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
       [shopping-cart-body @cart-items requester]
       [empty-cart])]))



(defmethod content/view :services [route]
  (let [header  (subscribe [:services/header])
        subhead (subscribe [:services/subhead])]
    [:div
     (typography/view-header @header @subhead)
     [:div.mb3
      [menu]]
     (content route)]))
