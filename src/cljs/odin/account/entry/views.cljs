(ns odin.account.entry.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [odin.l10n :as l10n]
            [odin.components.widgets :as widget]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]
            [odin.utils.formatters :as format]))

(defn progress-bar
  "A progress bar that changes color from green -> red as it approaches 100%."
  [progress-amount]
  (let [progress-class (if (> progress-amount 90) "danger" "ok")]
   [:div.progress-bar {:class progress-class}
    [:div.progress-bar-groove
     [:div.progress-bar-fill {:style {:width (str progress-amount "%")}}]]]))

(defn account-quicklook
  "Component to display high-level information for a Member."
  [account]
  (let [name         (str (get account :first_name) " " (get account :last_name))
        phone_number (get account :phone)
        email        (get account :email)]
   [:div.account-quicklook.flexbox
    [:div.account-entry-avatar.space-right
     [:img.account-entry-avatar-image {:src "/assets/images/bio-josh.jpg"}]]
    [:div.account-entry-metadata
     [:h2 name]
     [:p.account-contact-item
      ; [:span.icon.is-small [:i.fa.fa-envelope-o]]
      (format/email-link email)]
     [:p.account-contact-item
      ; [:span.icon.is-small [:i.fa.fa-phone]]
      (format/phone-number phone_number)]]]))

(defn license-display
  "Summarize a Member's license information."
  [account-id]
  (let [account (subscribe [:account/entry account-id])]
   [ant/card {}
    [:p "License"]]))

(defn metadata-list
  "Accepts vector of key/val pairs, to be displayed as a small table."
  [metadata]
  (fn []
    [:div.metadata-list
     (doall
      (for [[label value] metadata]
        ^{:key label} [:div.metadata-item
                       [:span.metadata-item-label label]
                       [:span.metadata-item-value value]]))]))


(defn membership-quicklook [account]
   [:div.account-quicklook.flexbox
    [:div.account-entry-metadata
     [:h4
      [:span.icon.is-small [:i.fa.fa-check-circle]]
      [:span "Rent Paid"]]
     [progress-bar 50]
     [progress-bar 92]
     [metadata-list [[(l10n/translate :property) (r/as-element [:a.subdued "West SoMa"])]
                     [(l10n/translate :unit)     (r/as-element [:a.subdued "#101"])]
                     [(l10n/translate :rent)     (format/currency 2300)]
                     [(l10n/translate :term)     (l10n/translate :months 6)]]]]])

(defn account-view [account-id]
  (let [account (subscribe [:account/entry account-id])]
    [:div.columns
     [:div.column
      [account-quicklook @account]
      [:div
       (str @account)]]
     [:div.column
      [membership-quicklook @account]]]))


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


;; TODO: Delete and replace with something useful. Just playing around with Ant
(defn test-component [title]
  [ant/card {:title title}
   [:nav {:class "panel"}
    ; [:p {:class "panel-heading"} "Recent Transactions"]
    ; [:p {:class "panel-tabs"}
     ; [:a {:class "is-active"} "All"]
     ; [:a "Payments"]
     ; [:a "Refunds"]
    [payment-list-item "Derryl Carter"   :payment "$500"   "Aug 1"  "ACH"]
    [payment-list-item "Josh Lehman"     :payment "$2,500" "Jul 28" "Check"]
    [payment-list-item "Mo Sakrani"      :refund  "$2,100" "Jul 28" "ACH"]
    [payment-list-item "Esteve Almirall" :payment "$2,200" "Jul 27" "ACH"]]])


(defn payments-list [title]
  [ant/card {:title title
             :class "flush-body"}
   [:table.table
    [:thead [:tr
             [:th "Amount"]
             [:th "Period"]
             [:th "Status"]
             [:th "Paid On"]
             [:th "Due Date"]
             [:th "Method"]
             [:th ""]]]
    [:tbody
     [:tr
      [:td (format/currency 2100)]
      [:td "8/1/17 - 8/31/17"]
      [:td "Paid"]
      [:td "8/1/17"]
      [:td "8/5/17"]
      [:td "AUTOPAY"]
      [:td [widget/stripe-icon-link ""]]]
     [:tr
      [:td (format/currency 2100)]
      [:td "8/1/17 - 8/31/17"]
      [:td "Paid"]
      [:td "8/1/17"]
      [:td "8/5/17"]
      [:td "AUTOPAY"]
      [:td [widget/stripe-icon-link ""]]]
     [:tr
      [:td (format/currency 2100)]
      [:td "8/1/17 - 8/31/17"]
      [:td "Paid"]
      [:td "8/1/17"]
      [:td "8/5/17"]
      [:td "AUTOPAY"]
      [:td [widget/stripe-icon-link ""]]]]]])


(defn fpo-note
  ([subject body]
   [ant/card {:title subject}
    [:p body]])
  ([subject]
   [ant/card {:title subject}
    [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."]]))

(defn notes-list []
  [ant/card {:title (l10n/translate :note/other)
             :extra (r/as-element [:a {:href ""}
                                   [:span.icon [:i.fa.fa-plus-square-o]]])}
   [fpo-note "Josh is on vacation" "He said he'll be in Wisconsin or something."]
   [fpo-note "He said he wants to use Clojure"]
   [fpo-note "Josh is joining the team!"]])



(defmethod content/view :account/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [:div

     [ant/breadcrumb {:separator "⟩"}
      [ant/breadcrumb-item [:a {:href "/"} [:span.icon.is-small [:i.fa.fa-home]]]]
      [ant/breadcrumb-item [:a {:href "/accounts"} "Accounts"]]
      [ant/breadcrumb-item [:span "Josh Lehman"]]]

     [:div.columns
      [:div.column
       [ant/card
        [account-view account-id]]
       [license-display account-id]
       [payments-list "Rent Payments"]]
      [:div.column
       [notes-list]]]]))
     ; [test-component]]))

; {:title "Accounts Entry"
          ; :extra (r/as-element [ant/button {:on-click #(dispatch [:account/fetch account-id])}
                                ; "Refresh"])}
