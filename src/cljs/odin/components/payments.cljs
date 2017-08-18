(ns odin.components.payments
  (:require [odin.l10n :as l10n]
            [odin.utils.toolbelt :as utils]
            [odin.utils.formatters :as format]
            [odin.profile.payments.sources.mocks :as mocks]
            [odin.components.notifications :as notification]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


(defn stripe-icon-link
  "Renders a Stripe icon that links to a transaction on Stripe."
  [uri]
  [ant/tooltip {:title     (l10n/translate :view-on-stripe)
                :placement "right"}
   [:a {:href uri}
    [:span.icon.is-small
     [:i.fa.fa-cc-stripe]]]])


(defn payment-source-icon
  "Renders an icon to illustrate a given payment source (such as :bank, :visa, or :amex). Defaults to :bank"
  ([source-type]
   [payment-source-icon source-type ""])
  ([source-type icon-size]
   [:span.icon {:class icon-size}
    (case source-type
      :amex [:i.fa.fa-cc-amex {:class icon-size}]
      :visa [:i.fa.fa-cc-visa {:class icon-size}]
      [:i.fa.fa-university {:class icon-size}])]))


(defn source-name-and-numbers [source]
  (let [{name   :name
         digits :trailing-digits} source]
    (str name " **** " digits)))


(defn payment-status
  "Displays payment status (paid / pending / etc)."
  [status]
  (case status
    "due"       [:span.tag.is-hollow "Due"]
    "canceled"  [:span.tag.is-hollow "Canceled"]
    "pending"   [:span.tag.is-hollow "Pending"]
    "failed"    [:span.tag.is-hollow "Failed"]
    "paid"      [:span.tag.is-hollow "Paid"]
    [:span]))


(defn payment-for
  "Displays payment status in tag format."
  [status]
  (case status
    "rent"    [:span.tag
               [:span.icon.extra-small [:i.fa.fa-home]]
               "Rent Payment"]
    "deposit" [:span.tag
               [:span.icon.extra-small [:i.fa.fa-shield]]
               "Security Deposit"]
    "order"   [:span.tag
               [:span.icon.extra-small [:i.fa.fa-smile-o]]
               "Service Order"]
    [:span]))


(defn render-payment-period
  "Takes tx. If period values exist, returns a string like '01/01/17 - 01/31/17'."
  [tx]
  (let [start (or (aget tx "pstart") (get tx :pstart))
        end   (or (aget tx "pend")   (get tx :pend))]
    (when (and start end)
      (str (format/date-short start)
           " - "
           (format/date-short end)))))


(def ^:private payment-table-columns
  [
   ;; PAYMENT TYPE ICON
   {:dataIndex :method
    :className "is-narrow width-2"
    :render    (fn [val]
                 (r/as-element [payment-source-icon val]))}

   ;; DATE PAID
   {:title     "Date"
    :dataIndex :paid_on
    :className "width-6"
    :render    (fn [val]
                 (format/date-short val))}

   ;; AMOUNT
   {:title     "Amount"
    :dataIndex :amount
    :className "td-bold width-4"
    :render    (fn [val]
                 (format/currency val))}

   ;; STATUS OF PAYMENT
   {:dataIndex :status
    :className "is-narrow width-5"
    :render    (fn [val]
                 (r/as-element [payment-status val]))}

   ;; REASON FOR PAYMENT
   {:title     "Type"
    :dataIndex :for
    :className "is-narrow width-8"
    :render    (fn [val]
                 (r/as-element [payment-for val]))}

   {:title     "Period"
                                        ; :dataIndex :for
    :className "expand"
    :render    (fn [val item _]
                 (render-payment-period item))}])


(defn get-payment-row-class
  "Returns a String class name for highlighting pending and due payments."
  [tx]
  (case (aget tx "status")
    ;; "due"     "warning"
    "pending" "info"
    "default"))


(defn payments-table
  "Receives a vector of transactions, and displays them as a list."
  [txs]
  [ant/table
   {:class        "payments-table"
    :loading      false
    :columns      payment-table-columns
    :rowClassName get-payment-row-class
    :dataSource   (map-indexed utils/thing->column txs)
    :pagination   false}])


(defn menu-select-source [sources]
  [:div.select
   [:select
    (for [source sources]
      (let [id (get source :id)]
        ^{:key id}
        [:option {:value id} (source-name-and-numbers source)]))]])


(defn render-payment-box [tx]
  (let [{reason :for
         amount :amount
         due    :due} tx]
    [:div.box
     [:div.columns
      [:div.column.is-narrow
       [:span.icon.is-large [:i.fa.fa-home]]]
                                        ; [:div.column.is-narrow]
                                        ; [:h3 (l10n/translate reason)]]
      [:div.column
       [:h3 (l10n/translate reason)]
       [:h3 [:a "2027 Mission St #101"]]
       [:h4 (render-payment-period tx)]]

      [:div.column.align-right
       [:h3 (format/currency amount)]
       (if (= reason (or :payment.for/rent :payment.for/deposit))
         [:p (str "Due on " (format/date-short due))])]]]))


(defn make-payment-modal [tx is-visible]
  (let [{reason :for
         amount :amount
         due    :due} tx]
    [ant/modal {:title     "Make a payment"
                :width     "640px"
                :visible   @is-visible
                :on-ok     #(reset! is-visible false)
                :on-cancel #(reset! is-visible false)
                :footer    [(r/as-element [menu-select-source mocks/payment-sources])
                                        ; (r/as-element [:a.button {:on-click #(reset! is-visible false)}
                                        ; "Cancel"])
                            (r/as-element [:a.button.is-success
                                           (str "Confirm Payment - " (format/currency amount))])]}
     [render-payment-box tx]]))


(defn rent-overdue-notification
  "Takes a tx in 'due' state, and displays a CTA notification for making payment."
  [tx]
  (let [modal-shown (r/atom false)]
    (fn [tx]
      [notification/banner-danger
       [:div
        [:p (l10n/translate :rent-overdue-notification-body (format/currency (get tx :amount)))
         [:a {:on-click #(swap! modal-shown not)} (l10n/translate :rent-overdue-notification-body-cta)]
         [make-payment-modal tx modal-shown]]]])))
