(ns admin.profile.views
  (:require [antizer.reagent :as ant]
            [cljs.core.match :refer-macros [match]]
            [iface.components.menu :as menu]
            [admin.content :as content]
            [member.l10n :as l10n]
            [admin.profile.membership.views :as membership]
            [admin.profile.payments.history.views :as phistory]
            [admin.profile.payments.sources.views :as psources]
            [admin.profile.contact.views :as contact]
            [admin.profile.settings.views :as settings]
            [admin.routes :as routes]))


(def ^:private profile-menu-spec
  [{:label    (l10n/translate :profile)
    :children [{:label (l10n/translate :membership)
                :key   :profile/membership
                :route (routes/path-for :profile/membership)}
               {:label (l10n/translate :contact-info)
                :key   :profile/contact
                :route (routes/path-for :profile/contact)}]}
   {:label    (l10n/translate :payments)
    :children [{:label (l10n/translate :history)
                :key   :profile.payment/history
                :route (routes/path-for :profile.payment/history)}
               {:label (l10n/translate :sources)
                :key   :profile.payment/sources
                :route (routes/path-for :profile.payment/sources)}]}
   {:label    (l10n/translate :settings)
    :children [{:label (l10n/translate :change-password)
                :key   :profile.settings/change-password
                :route (routes/path-for :profile.settings/change-password)}
               {:label (l10n/translate :log-out)
                :route "/logout"}]}])


(defn mobile-nav-item
  [icon text is-active]
  [:li {:class (when is-active "is-active")}
      [:a {:href "#"} [ant/icon {:type icon}] [:span.nav-label text]]])


(defn mobile-nav []
  [:div.mobile-nav-container
   [:ul.mobile-page-nav
    [mobile-nav-item "user" "Membership"]
    [mobile-nav-item "credit-card" "Payments" true]
    [mobile-nav-item "contacts" "Account"]]
   [:div.tabs
    [:ul
     [:li [:a
           [ant/icon {:type "bars"}]
           [:span "History"]]]
     [:li.is-active [:a
                     [ant/icon {:type "credit-card"}]
                     [:span "Payment Methods"]]]]]])


(defn content
  "The top-level content for all `:profile` views. Sets up a side menu for
  sub-navigation and a container for sub-content."
  [{:keys [page path] :as route}]
  [:div
   ;;[mobile-nav]
   [:div.columns
    [:div.column.sidebar ;;is-one-fifth
     [menu/side-menu profile-menu-spec page]]
    [:div.column
     (let [path (vec (rest path))]
       (match [path]
         [[:membership]] [membership/membership]
         [[:contact]] [contact/contact-info]
         [[:payment :history]] [phistory/history route]
         [[:payment :sources]] [psources/sources]
         [[:settings :change-password]] [settings/change-password]
         :else [:h1 "unmatched"]))]]])


(defmethod content/view :profile [route]
  [content route])
