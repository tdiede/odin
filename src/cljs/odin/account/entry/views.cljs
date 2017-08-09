(ns odin.account.entry.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [odin.l10n :refer [translate]]
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
     [:h4.account-contact-item
      [:span.icon.is-small [:i.fa.fa-envelope-o]]
      (format/email-link email)]
     [:h4.account-contact-item
      [:span.icon.is-small [:i.fa.fa-phone]]
      (format/phone-number phone_number)]]]))

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

(defn header-with-icon
  [icon-type text]
  (let [size :h4]
    [size
     [:span.icon.is-small [:i {:class (str "fa fa-" icon-type)}]]
     [:span text]]))


(defn membership-quicklook [account]
   [:div.account-quicklook.flexbox
    [:div.account-entry-metadata
     [:h4
      [:span.icon.is-small [:i.fa.fa-check-circle]]
      [:span "Member"]]
     ; [header-with-icon "home" "Member"]
     [progress-bar 50]
     [progress-bar 92]
     [metadata-list [[(translate :property) (r/as-element [:a.subdued "West SoMa"])]
                     ["Unit" (r/as-element [:a.subdued "#101"])]
                     ["Rent" (format/currency 2300)]
                     ["Term" (translate :months 6)]]]]])

(defn account-view [account-id]
  (let [account (subscribe [:account/entry account-id])]
    [:div.columns
     [:div.column
      [account-quicklook @account]]
     [:div.column
      [membership-quicklook @account]]
     [:div.column
      [:div
       (str @account)]]]))


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
     [ant/breadcrumb {:separator "⟩"}
      [ant/breadcrumb-item [:a {:href "/"} [:span.icon.is-small [:i.fa.fa-home]]]]
      [ant/breadcrumb-item [:a {:href "/accounts"} "Accounts"]]
      [ant/breadcrumb-item [:span "Josh Lehman"]]]
     [ant/card
      ; {:title "Accounts Entry"
                ; :extra (r/as-element [ant/button {:on-click #(dispatch [:account/fetch account-id])}
                                      ; "Refresh"])}

      [account-view account-id]]
     [:br]
     [:div.columns
      [:div.column
       [:h1.has-debug "Heading 1"]
       [:h2.has-debug "Heading 2"]
       [:h3.has-debug "Heading 3"]
       [:h4.has-debug "Heading 4"]
       [:h5.has-debug "Heading 5"]]
      [:div.column
       [:h2 "Other Stuff"]]]]))
     ; [test-component]]))
