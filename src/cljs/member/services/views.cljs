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
     [ant/menu-item {:key "active-orders" :style {:float "right"}} "Active orders"]
     ]))


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


;; Do we prefer the "edit" button on the left or the right of the card?
;; I like keeping all the buttons on the right... but it looks so wide...

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


;; QUESTION: Do we need better language on how premium services will be charged individually?
;; TODO if there is no credit card on file we need to collect credit card information
(defn shopping-cart-footer [requester]
  (let [has-card (subscribe [:payment-sources/has-card? (:id requester)])]
    (.log js/console "has card? " @has-card)
    [:div.cart-footer.has-text-right
    [:p.fs2
     [:b "NOTE: "] "Premium Service requests are treated as individual billable items. You will be charged for each service as it is fulfilled."]
    [ant/button {:class "ant-btn-xl"
                 :type "primary"
                 :on-click (fn []
                             (if-not @has-card
                               (.log js/console "Need to enter a credit card")
                               (dispatch [:services.cart/submit]))
                             )}
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
   [:h3 "Manage some services, yo"]])


(defmethod content :services/subscriptions [_]
  [:div
   [:h3 "Manage your subscriptions, yo"]])


(defmethod content :services/history [_]
  [:div
   [:h3 "Look at all the things youve ordered, yo"]])



(defmethod content :services/cart [{:keys [requester] :as route}]
  (let [cart-items (subscribe [:services.cart/cart])]
    [:div
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
