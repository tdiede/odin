(ns odin.profile.membership.views
   (:require [odin.l10n :as l10n]
             [odin.components.membership :as member-ui]
             [odin.components.orders :as orders-ui]
             [odin.utils.formatters :as format]
             [odin.components.notifications :as notification]
             [antizer.reagent :as ant]))


(def mock-member-license {:active            true
                          :term              12
                          :price             1400
                          :commencement-date (format/str->timestamp "Jun 1, 2017")
                          :end-date          (format/str->timestamp "Jul 31, 2018")
                          :unit              {:name        "#301"
                                              :description "One-bedroom unit with large windows."
                                              :floor       3
                                              :dimensions  {:height 10
                                                            :width  14
                                                            :length 14}
                                              :property    "2072 Mission"}})

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


(defn card-license-summary
  [license]
  (let [{term  :term
         price :price
         start :commencement-date
         end   :end-date
         {community-name :property
          unit-number    :name} :unit} license]
   [:div.card.align-center
    [:div.card-image
     [:figure.image
      [:img {:src "/assets/images/communal-1000x500.jpg"}]]]
    [:div.card-content
     [:div.content
      [:h3 (str community-name " " unit-number)]
      [:h4 (str term " months • " (format/currency price) "/mo.")]
      [:p (str (format/date-short start) " - " (format/date-short end))]]]]))
    ;;[:footer.card-footer
    ;; [:a.card-footer-item
    ;;  [:span.icon.is-small [:i.fa.fa-file-text]]
    ;;  [:span.with-icon "View Agreement"]]]]))



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


(defn membership-summary []
  [:div
   [notification/banner-success "Good news – You're all paid up! Your next rent payment of $1,400 is due on September 4th."]
   [notification/banner "Your next payment is due "]])




(defn membership []
  [:div
   [:div.view-header
    [:h1 (l10n/translate :membership)]]
    ;;[:p "View and manage your rental agreement and any premium subscriptions you've signed up for."]]
   ;;[:br]

   [:div.columns
    [:div.column
     [:h2 "Summary"]
     [membership-summary]]
     ;;[:h2 "Subscriptions"]
     ;;(for [service mock-services]
       ;;^{:key (get service :id)}
       ;;[card-service-summary service])
     ;;[:h4 [:a "View all services"]]]

    [:div.column.is-5
     [:h2 "Rental Agreement"]
     [card-license-summary mock-member-license]]]])

   ;;[:p "Rent plus subscriptions: " [:b "$1,545 / mo."]]])
   ;;[:hr]])
