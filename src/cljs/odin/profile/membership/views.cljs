(ns odin.profile.membership.views
  (:require [odin.l10n :as l10n]
            [re-frame.core :refer [subscribe dispatch]]
            [odin.components.membership :as member-ui]
            [odin.components.orders :as orders-ui]
            [odin.utils.formatters :as format]
            [odin.utils.time :as t]
            [odin.components.notifications :as notification]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]))


(def mock-services [{:id          13886565
                     :name        "Plant Service"
                     :icon        "fa-pagelines"
                     :price       10
                     :rental      true
                     :description "Beautiful plant to keep your room happy and full of oxygen!"}
                    {:id          87986643
                     :name        "Deep-Tissue Massage"
                     :icon        "fa-hand-paper-o"
                     :price       60
                     :rental      true
                     :description "Align your chakras with a weekly, relaxing massage."}
                    {:id          87982243
                     :name        "Enigma 3-Class Package"
                     :icon        "fa-magic"
                     :price       75
                     :rental      true
                     :description "Learn cool things from visitors to your community."}])



(defn card-license-summary []
  (let [license (subscribe [:member/license])
        {:keys [term rate starts ends property unit]} @license]
    [ant/card {:loading @(subscribe [:member.license/loading?])
               :class   "is-flush"}
     (when-not (nil? rate)
       [:div
        [:div.card-image
         [:figure.image
          [:img {:src (:cover_image_url property)}]]]
        [:div.card-content
         [:div.content
          [:h3 (str (:name property) " #" (:number unit))]
          [:h4 (str term " months • " (str (format/date-short starts) " - " (format/date-short ends)))]
          [:p (str (format/currency rate) "/mo.")]]]
        ;; If a link to view PDF version of license is provided, show it here
        (when-not (nil? (:view-link @license))
          [:footer.card-footer
           [:a.card-footer-item
            [:span.icon.is-small [:i.fa.fa-file-text]]
            [:span.with-icon "View Agreement"]]])])]))



(defn card-service-summary
  [service]
  (let [{price :price
         name  :name
         icon  :icon
         desc  :description} service]
    [:div.box
     [:article.media
      [:div.media-left
       [:span.icon.is-large [:i.fa {:class icon}]]]
      [:div.media-content
       [:h4 (str name " (" (format/currency price) "/mo.)")]
       [:p.smaller desc]]
      [:div.media-right
       [:a "Manage"]]]]))


(defn deposit-status-card
  []
  (let [deposit    (subscribe [:profile/security-deposit])
        is-overdue (t/is-before-now (:due @deposit))]
    [:div.mb2
     ;;[:h4
     ;; [:span.icon [:i.fa.fa-shield]]
     ;; [:span "Security Deposit"]]
     [:div.card
      [:div.card-content
       [:div.columns
        [:div.column.is-2
         [:span.icon.is-large.text-yellow [:i.fa.fa-shield]]]
        [:div.column
         [:h4 "Security deposit partially paid."]
         ;;[:p (format/string
         ;;      "You owe another %s by %s."
         ;;      (format/currency   (:amount_remaining @deposit))
         ;;      (format/date-short (:due @deposit)))]
         [ant/button "Pay remaining amount ($1,800)"]]]]]]))


(defn rent-status-card
  []
  (let [license (subscribe [:member/license])]
   [:div.mb2
    ;;[:h4
    ;; [:span.icon [:i.fa.fa-home]]
    ;; [:span "Rent"]]
    [ant/card ;;{:title "Rent Status"}
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


(defn membership []
  [:div
   [:div.view-header
    [:h1 (l10n/translate :membership)]]
   ;;[:p "View and manage your rental agreement and any premium subscriptions you've signed up for."]]
   ;;[:br]

   [:div.columns
    [:div.column.is-5
     [:h2 "Rental Agreement"]
     [card-license-summary]]

    [:div.column
     [membership-summary]]]])
