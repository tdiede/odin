(ns odin.components.payments
  (:require [odin.l10n :as l10n]
            [odin.utils.toolbelt :as utils]
            [odin.utils.formatters :as format]
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
  "Displays payment status. If 'paid', nothing is displayed."
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

(defn payment-list-item [payment]
  (let [amount  (get payment :amount)
        type    (get payment :for)
        status  (get payment :status)
        paid-on (get payment :paid-on)
        method  (get payment :method)]
   [:a.panel-block.payment-item
    ;; Type
    [payment-source-icon method "is-small"]
    ;; Amount
    [:span (format/currency amount)]
    ;; Reason
    [:span.has-text-grey-light type]
    ;; Paid On
    [:span.date (.format (js/moment. (js/Date.)) "ll")]
    ;; Status
    (case status
      :payment.status/pending [ant/tag {:color "yellow"} method]
      [ant/tag {:color "green"} method])]))


(defn payments-list
  "Receives a vector of transactions, and displays them as a list."
  [txs]
  [:nav {:class "panel"}
    (for [tx txs]
      ^{:key (get tx :id)}
      [payment-list-item tx])])

;; Payment Table

(def ^:private payment-table-columns
  [
   ; {:title     ""
   ;  :dataIndex :method
   ;  :render    (fn [val]
   ;               (r/as-element
   ;                [payment-source-icon val]))}
   {:title     "Amount"
    :dataIndex :amount
    :className "align-right"
    ; :sorter    (utils/comp-alphabetical :amount)
    :render    (fn [val]
                 (format/currency val))}
   {:title     ""
    :dataIndex :status
    :className "narrow"
    :render    (fn [val]
                 (r/as-element
                  [payment-status val]))}
    ;; :sorter    (utils/comp-alphabetical :last_name)}
   {:title     "Type"
    :dataIndex :for
    :render    (fn [val]
                 (r/as-element
                  [payment-for val]))}
    ;; :sorter    (utils/comp-alphabetical :email)
   {:title     "Date"
    :dataIndex :paid-on
    :render    (fn [val]
                 (format/date-short val))}])
    ;; :sorter    (utils/comp-alphabetical :phone)

(defn tx->column [key tx]
  (assoc tx :key key))


(defn get-payment-row-class
  "Returns a class name for highlighting pending and due payments."
  [tx]
  (case (aget tx "status")
    "due"     "warning"
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
    :dataSource   (map-indexed tx->column txs)
    :pagination   false}])
