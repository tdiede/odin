(ns odin.components.payments
  (:require [odin.l10n :as l10n]
            [odin.utils.formatters :as format]
            [odin.utils.time :as time]
            [odin.components.notifications :as notification]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]
            [clojure.string :as string]
            [iface.table :as table]))


(defn stripe-icon-link
  "Renders a Stripe icon that links to a transaction on Stripe."
  [uri]
  [ant/tooltip {:title     (l10n/translate :view-on-stripe)
                :placement "right"}
   [:a {:href uri}
    [:span.icon.is-small
     [:i.fa.fa-cc-stripe]]]])


(defn payment-source-icon
  "Renders an icon to illustrate a given payment source (such as :bank, :visa,
  or :amex). Defaults to :bank."
  ([source-type]
   [payment-source-icon source-type ""])
  ([source-type icon-size]
   [:span.icon {:class icon-size}
    (case source-type
      :card [:i.fa.fa-credit-card {:class icon-size}]
      [:i.fa.fa-university {:class icon-size}])]))


(defn source-name
  ([source]
   (when-some [{:keys [name last4]} source]
     (source-name name last4)))
  ([name last4]
   (str name " **** " last4)))


(defn payment-status
  "Displays payment status (paid / pending / etc)."
  [status]
  (letfn [(-hollow [txt]
            [:span.tag.is-hollow txt])]
    (case status
      "due"      (-hollow "Due")
      "canceled" (-hollow "Canceled")
      "pending"  (-hollow "Pending")
      "failed"   (-hollow "Failed")
      "paid"     (-hollow "Paid")
      [:span])))


(defn payment-for
  "Displays payment status in tag format."
  [status]
  (letfn [(-tag [icon txt]
            [:span.tag
             [:span.icon.extra-small [:i {:class (str "fa " icon)}]]
             txt])]
    (case status
      "rent"    (-tag "fa-home" "Rent Payment")
      "deposit" (-tag "fa-shield" "Security Deposit")
      "order"   (-tag "fa-smile-o" "Service Order")
      [:span])))


(defn payment-for-icon
  "Renders an icon to illustrate a given payment type (such as :rent, :deposit, :order, or :other). Defaults to :other"
  ([reason]
   [payment-for-icon reason ""])
  ([reason icon-size]
   (when reason
     [ant/tooltip {:title     (l10n/translate (keyword "payment.for" reason))
                   :placement "right"}
      [:span.icon.has-tooltip {:class icon-size}
       (case reason
         "rent"    [:i.fa.fa-home    {:class icon-size}]
         "deposit" [:i.fa.fa-shield  {:class icon-size}]
         "order"   [:i.fa.fa-smile-o {:class icon-size}]
         "")]])))


(defn render-payment-date
  "Date appears red if it was overdue."
  [paid_on due]
  [:span (format/date-short paid_on)])


(defn render-paid-on
  [{:keys [paid_on] :as payment}]
  (let [due (when-some [d (:due payment)] (js/moment. d))]
    (cond
      (some? paid_on)
      [:span (format/date-short paid_on)]

      (and (some? due) (.isAfter (js/moment. paid_on) due))
      [ant/tooltip {:title "This payment was received late."}
       [:span.text-red (format/date-short paid_on)]]

      :otherwise
      [:div.has-text-centered {:dangerouslySetInnerHTML {:__html "&mdash;"}}])))


(defn payment-table-columns [columns]
  (->> [;; payment type icon
        {:dataIndex :type
         :className "is-narrow width-2"
         :render    (table/wrap-cljs (fn [type] [payment-for-icon type]))}

        ;; date paid
        {:title     (l10n/translate :paid-on)
         :dataIndex :paid_on
         :className "width-6"
         :render    (table/wrap-cljs
                     (fn [_ payment]
                       [render-paid-on payment]))}

        ;; amount
        {:title     (l10n/translate :amount)
         :dataIndex :amount
         :className "td-bold width-4 text-larger"
         :render    (table/wrap-cljs (fn [amount] (format/currency amount)))}

        ;; status of payment
        {:dataIndex :status
         ;;:className "is-narrow width-5"
         :render    (table/wrap-cljs (fn [status] [payment-status status]))}

        ;; description
        {:title     (l10n/translate :description)
         :dataIndex :description
         :className "expand"
         :render    (fn [val] (r/as-element [:span.yes-wrap.restrict-width val]))}

        ;; method
        {:title "Method"
         :dataIndex :method
         :render (table/wrap-cljs
                  (fn [method payment]
                    (when-some [m method]
                      [:span.tag.is-hollow m])))}

        ;; payment source
        {:title     ""
         :dataIndex :source
         :className "align-right light"
         :render    (fn [val item _] (source-name (js->clj val :keywordize-keys true)))}]
       (filter (comp columns :dataIndex))))


(defn get-payment-row-class
  "Returns a String class name for highlighting pending and due payments."
  [payment]
  (case (aget payment "status")
    "due"     "warning"
    "pending" "info"
    "default"))


(def default-columns
  #{:type :paid_on :amount :status :description :source})


(defn payments-table
  "Receives a vector of transactions, and displays them as a list."
  [payments loading? & {:keys [columns] :or {columns default-columns}}]
  (let [;; payments (if (every? (comp some? :created) payments)
        ;;            (reverse (sort-by :created payments))
        ;;            payments)
        payments (reverse (sort-by (fn [{:keys [paid_on created]}]
                                     (or paid_on created))
                                   payments))]
    [ant/table
     {:class        "payments-table"
      :loading      (or loading? false)
      :columns      (payment-table-columns columns)
      :rowClassName get-payment-row-class
      :dataSource   payments
      :locale       {:emptyText (l10n/translate :payment-table-no-payments)}}]))


(defn menu-select-source [sources]
  [:div.select
   [:select
    (for [source sources]
      (let [id (get source :id)]
        ^{:key id}
        [:option {:value id} (source-name source)]))]])


(defn- icon-class [payment-type]
  (case payment-type
    :deposit "fa-shield"
    :rent    "fa-home"
    :order   "fa-smile-o"
    ""))


(defn payment-box
  [{:keys [type amount due] :as payment} description]
  [:div.box
   [:div.columns
    [:div.column.is-narrow
     [:span.icon.is-large [:i {:class (str "fa fa-3x " (icon-class type))}]]]
    [:div.column
     [:h3 (l10n/translate (:type payment))]
     (when (some? description)
       [:h3 description])
     [:h4 (string/capitalize (:description payment))]]

    [:div.column.align-right
     [:h3 (format/currency amount)]
     (if (#{:rent :deposit} type)
       [:p (str "Due by " (format/date-short due))])]]])


(defn- make-payment-modal-footer
  [payment-id {:keys [on-confirm on-cancel loading sources]
               :or   {on-confirm #(dispatch [:modal/hide payment-id])
                      loading    false}}]
  (let [selected-source (r/atom (-> sources first :id))]
    (fn [payment-id {:keys [on-confirm on-cancel loading sources]
                    :or   {on-confirm #(dispatch [:modal/hide payment-id])
                           loading    false}}]
      [:div
       (when (some? sources)
         [ant/select
          {:style {:width 225 :margin-right "1rem"}
           :size  :large
           :value @selected-source}
          (for [{id :id :as source} sources]
            ^{:key id} [ant/select-option {:value id} (source-name source)])])
       [ant/button
        {:size     :large
         :on-click on-cancel}
        "Cancel"]
       [ant/button
        {:type     :primary
         :size     :large
         :on-click (fn [e]
                     (if-some [source-id @selected-source]
                       (on-confirm payment-id source-id e)
                       (on-confirm payment-id e)))
         :loading  loading}
        "Confirm Payment"]])))


(defn make-payment-modal
  [{:keys [id type amount due] :as payment} & {:keys [on-cancel desc]
                                               :or   {on-cancel #(dispatch [:modal/hide id])}
                                               :as   opts}]
  (let [visible (subscribe [:modal/visible? id])]
    [ant/modal {:title     "Make a payment"
                :width     "640px"
                :visible   @visible
                :on-cancel on-cancel
                :footer    (let [opts (merge {:on-cancel on-cancel} opts)]
                             (r/as-element
                              [make-payment-modal-footer (:id payment) opts]))}
     [payment-box payment desc]]))


(defn rent-overdue-notification
  "Takes a payment in 'due' state, and displays a CTA notification for making payment."
  [payment]
  (let [modal-shown (r/atom false)]
    (fn [payment]
      [notification/banner-danger
       [:div
        [:p (l10n/translate :rent-overdue-notification-body (format/currency (get payment :amount)))
         [:a {:on-click #(swap! modal-shown not)} (l10n/translate :rent-overdue-notification-body-cta)]
         [make-payment-modal payment modal-shown]]]])))


;; add check ====================================================================


(defn- check-payment-option
  [{:keys [id amount type description]}]
  [ant/radio {:value id}
   [:span
    (format/currency amount)
    " - "
    description]])


(defn- select-payment
  ([payments form-data]
   (select-payment (:payment @form-data) payments form-data))
  ([payment-id payments form-data]
   (let [payment (tb/find-by #(= payment-id (:id %)) payments)]
     {:payment payment-id
      :amount   (:amount payment)})))


(defn add-check-modal-footer
  [id {:keys [payment amount name received-date check-date] :as data} on-submit]
  (let [can-submit (and payment amount name received-date check-date)
        is-loading (subscribe [:loading? id])]
    [:div
     [ant/button
      {:size     :large
       :on-click #(dispatch [:modal/hide id])}
      "Cancel"]
     [ant/button
      {:type     :primary
       :size     :large
       :disabled (not can-submit)
       :on-click #(on-submit data)
       :loading  @is-loading}
      "Add Check"]]))


(defn add-check-modal
  [id payments & {:keys [on-submit] :or {on-submit identity}}]
  (let [visible (subscribe [:modal/visible? id])
        data    (r/atom {:payment (:id (first payments))
                         :amount  (:amount (first payments))
                         :name    ""})]
    (fn [id payments]
      [ant/modal
       {:title     "Add a Check"
        :visible   @visible
        :on-cancel #(dispatch [:modal/hide id])
        :footer (r/as-element [add-check-modal-footer id @data on-submit])}

       [ant/form-item {:label "Choose Payment"}
        [ant/radio-group
         {:on-change #(swap! data merge (select-payment (.. % -target -value) payments data))
          :value     (:payment @data)}
         (doall
          (map-indexed
           #(with-meta (check-payment-option %2) {:key %1})
           payments))]]

       [:div.columns
        [:div.column
         [ant/form-item {:label "Amount"}
          [ant/input-number
           {:value     (:amount @data)
            :on-change #(swap! data assoc :amount %)
            :style     {:width "100%"}}]]]
        [:div.column
         [ant/form-item {:label "Name on Check"}
         [ant/input
          {:value     (:name @data)
           :on-change #(swap! data assoc :name (.. % -target -value))}]]]]

       [:div.columns
        [:div.column
         [ant/form-item {:label "Date on Check"}
          [ant/date-picker
           {:value     (when-let [d (:check-date @data)] (js/moment. d))
            :on-change #(swap! data assoc :check-date (.toISOString %))}]]]

        [:div.column
         [ant/form-item {:label "Date Received"}
          [ant/date-picker
           {:value     (when-let [d (:received-date @data)] (js/moment. d))
            :on-change #(swap! data assoc :received-date (.toISOString %))}]]] ]])))
