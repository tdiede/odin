(ns odin.profile.views
  (:require [cljs.core.match :refer-macros [match]]
            [odin.l10n :as l10n]
            [odin.content :as content]
            [odin.profile.membership.views :as membership]
            [odin.profile.payments.history.views :as phistory]
            [odin.profile.payments.sources.views :as psources]
            [odin.profile.contact.views :as contact]
            [odin.profile.settings.views :as settings]
            [odin.components.navigation :as navigation]
            [toolbelt.core :as tb]))


(def ^:private profile-menu-spec
  [{:label    (l10n/translate :profile/member)
    :children [{:label (l10n/translate :membership)
                :route :profile/membership}
               {:label (l10n/translate :contact-info)
                :route :profile/contact}]}
   {:label    (l10n/translate :payments)
    :children [{:label (l10n/translate :history)
                :route :profile.payment/history}
               {:label (l10n/translate :sources)
                :route :profile.payment/sources}]}
   {:label    (l10n/translate :settings)
    :children [{:label (l10n/translate :change-password)
                :route :profile.settings/change-password}
               {:label (l10n/translate :log-out)
                :route "/logout"}]}])


(defn content
  "The top-level conrtent for all `:profile` views. Sets up a side menu for
  sub-navigation and a container for sub-content."
  [{:keys [page path] :as route}]
  [:div.columns
   [:div.column.is-one-fifth
    [navigation/side-menu profile-menu-spec page]]
   [:div.column
    (let [path (vec (rest path))]
      ;;(tb/log path)
      (match [path]
        [[:membership]] [membership/membership]
        [[:contact]] [contact/contact-info]
        [[:payment :history]] [phistory/history]
        [[:payment :sources]] [psources/sources]
        [[:settings :change-password]] [settings/change-password]
        :else [:h1 "unmatched"]))]])

(defmethod content/view :profile [route]
  [content route])
