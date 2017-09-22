(ns odin.profile.membership.views
  (:require [antizer.reagent :as ant]
            [iface.typography :as typography]
            [odin.l10n :as l10n]
            [odin.utils.formatters :as format]
            [odin.utils.time :as t]
            [re-frame.core :refer [dispatch subscribe]]))


(defn card-license-summary []
  (let [license (subscribe [:member/license])
        loading (subscribe [:member.license/loading?])
        {:keys [term rate starts ends property unit]} @license]
    [ant/card {:loading @(subscribe [:member.license/loading?])
               :class   "is-flush"}
     (when-not (nil? rate)
       [ant/card {:loading @loading :class "is-flush"}
        [:div.card-image
         [:figure.image
          [:img {:src (:cover_image_url property)}]]]
        [:div.card-content
         [:div.content
          [:h3.title.is-4 (str (:name property) " #" (:number unit))]
          [:h4 (str term " months • " (str (format/date-short starts) " - " (format/date-short ends)))]
          [:p (str (format/currency rate) "/mo.")]]]
        ;; If a link to view PDF version of license is provided, show it here
        ;;(when-not (nil? (:view-link @license))
        [:footer.card-footer
         [:a.card-footer-item
          [:span.icon.is-small [:i.fa.fa-file-text]]
          [:span.with-icon "View Agreement"]]]])]))



;;(defn card-service-summary
;;  [service]
;;  (let [{price :price
;;         name  :name
;;         icon  :icon
;;         desc  :description} service]
;;    [:div.box
;;     [:article.media
;;      [:div.media-left
;;       [:span.icon.is-large [:i.fa {:class icon}]]]
;;      [:div.media-content
;;       [:h4 (str name " (" (format/currency price) "/mo.)")]
;;       [:p.smaller desc]]
;;      [:div.media-right
;;       [:a "Manage"]]]]))


(defn deposit-status-card
  []
  (let [loading    (subscribe [:member.license/loading?])
        deposit    (subscribe [:profile/security-deposit])
        is-overdue (t/is-before-now (:due @deposit))]
    [:div.mb2
     ;;[:h4
     ;; [:span.icon [:i.fa.fa-shield]]
     ;; [:span "Security Deposit"]]
     [ant/card {:loading @loading}
       [:div.columns
        [:div.column.is-2
         [:span.icon.is-large.text-yellow [:i.fa.fa-shield]]]
        [:div.column
         [:h4 "Security deposit partially paid."]
         ;;[:p (format/string
         ;;      "You owe another %s by %s."
         ;;      (format/currency   (:amount_remaining @deposit))
         ;;      (format/date-short (:due @deposit)))]
         [ant/button "Pay remaining amount ($1,800)"]]]]]))


(defn rent-status-card
  []
  (let [license (subscribe [:member/license])
        loading (subscribe [:member.license/loading?])]
   [:div.mb2
    [ant/card {:loading @loading}
     [:div.columns
      [:div.column.is-2
       [:span.icon.is-large.text-green [:i.fa.fa-home]]]
      [:div.column
       [:h4 "Rent is paid."]
       [:p "Congratulations! You're paid for the month of September."]]]]]))



(defn membership-summary []
  [:div
   [:h2 "Status"]
   [deposit-status-card]
   [rent-status-card]])

(defn btn-refetch-data []
  (let [account-id (subscribe [:profile/account-id])]
    [ant/button {:size    "large"
                 :on-click #(dispatch [:member/fetch-license @account-id])}
     "Re-fetch data"]))


(defn membership []
  [:div
   (typography/view-header
    (l10n/translate :membership))

   [:div.columns
    [:div.column.is-5
     [:h2 "Rental Agreement"]
     [card-license-summary]]

    [:div.column
     [membership-summary]]]])
