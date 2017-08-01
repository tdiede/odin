(ns odin.account.entry.views
  (:require [odin.views.content :as content]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]))


(defn account-view [account-id]
  (let [account (subscribe [:account/entry account-id])]
    [:div
     (str @account)]))

(defn payment-list-item [name type amount date method]
  [:a.panel-block.payment-item
   [:span.icon.is-small [:i.fa.fa-university]]
   [:span name]
   [:span.has-text-grey-light type]
   (case type
     :payment [:span.flex-right amount]
     :refund  [:span.flex-right.red amount])
   [:span.date (.format (js/moment. (js/Date.)) "ll")]
   (case method
     "ACH"   [:span.tag.is-success method]
     "Check" [:span.tag.is-info method])])


(defn test-component []
  [ant/card {:title "2072 Mission"}
   [:nav {:class "panel"}
    [:p {:class "panel-heading"} "Recent Transactions"]
    [:p {:class "panel-tabs"}
     [:a {:class "is-active"} "All"]
     [:a "Payments"]
     [:a "Refunds"]]
    [payment-list-item "Derryl Carter" :payment "$500" "Aug 1" "ACH"]
    [payment-list-item "Josh Lehman" :payment "$2,500" "Jul 28" "Check"]
    [payment-list-item "Mo Sakrani" :refund "$2,100" "Jul 28" "ACH"]
    [payment-list-item "Esteve Almirall" :payment "$2,200" "Jul 27" "ACH"]]])


(defmethod content/view :account/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [:div
     [ant/card {:title "Accounts Entry"}
      [ant/button {:on-click #(dispatch [:account/fetch account-id])}
       "Refresh"]
      [account-view account-id]]
     [:br]
     [test-component]]))
