(ns odin.components.orders
  (:require [odin.l10n :as l10n]
            [odin.utils.toolbelt :as utils]
            [odin.utils.formatters :as format]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [reagent.core :as r]))

(def mock-orders
  []
  #_[{:id          13886565
    :name        "Plant Service"
    :paid-on     (format/str->timestamp "Aug 5, 2017")
    :amount      10
    :rental      true}
   {:id          87986643
    :name        "Deep-Tissue Massage"
    :paid-on     (format/str->timestamp "Aug 5, 2017")
    :amount      60
    :rental      true}
   {:id          87982243
    :name        "Enigma 3-Class Package"
    :paid-on     (format/str->timestamp "Aug 5, 2017")
    :amount      75
    :rental      true}])

(def ^:private order-history-columns
  [
   ;; DATE PAID
   {:title     "Date"
    :dataIndex :paid-on
    :className "width-6"
    :render    (fn [val] (format/date-short val))}

   ;; AMOUNT
   {:title     "Amount"
    :dataIndex :amount
    :className "td-bold align-center width-6"
    :render    (fn [val] (format/currency val))}

   ;; STATUS OF PAYMENT
   {:title     "Service"
    :dataIndex :name}])
                                        ; :className "width-5"

;; REASON FOR PAYMENT
                                        ; {:title     "Type"
                                        ;  :dataIndex :for
                                        ;  :className "is-narrow width-8"
                                        ;  :render    (fn [val]
                                        ;               (r/as-element [payment-for val]))}
                                        ;
                                        ; {:title     "Period"
                                        ;  ; :dataIndex :for
                                        ;  :className "expand"
                                        ;  :render    (fn [val item _]
                                        ;               (render-payment-period item))}])


(defn order-history
  [orders]
  [ant/table
   {:class        "order-history-table"
    :loading      false
    :columns      order-history-columns
    :dataSource   (map-indexed utils/thing->column orders)}])
                                        ; [:div
                                        ;  (for [order orders]
                                        ;    ^{:key (get order :id)}
                                        ;    [:p (get order :name)])])
