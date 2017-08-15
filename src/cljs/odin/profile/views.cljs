(ns odin.profile.views
  (:require [cljs.core.match :refer-macros [match]]
            [odin.content :as content]
            [odin.profile.membership.views :as membership]
            [odin.profile.payments.history.views :as phistory]
            [odin.profile.payments.sources.views :as psources]
            [odin.profile.settings.views :as settings]
            [odin.components.navigation :as navigation]
            [toolbelt.core :as tb]))


(def ^:private profile-menu-spec
  [{:label    "Profile"
    :children [{:label "Membership"
                :route :profile/membership}
               {:label "Contact Info"
                :route :profile/contact}]}
   {:label    "Payments"
    :children [{:label "History"
                :route :profile.payment/history}
               {:label "Sources"
                :route :profile.payment/sources}]}
   {:label    "Settings"
    :children [{:label "Change Password"
                :route :profile.settings/change-password}
               {:label "Log Out"
                :route "/logout"}]}])


(defn content
  "The top-level conrtent for all `:profile` views. Sets up a side menu for
  sub-navigation and a container for sub-content."
  [{:keys [page path] :as route}]
  [:div.columns
   [:div.column.is-one-quarter
    [navigation/side-menu profile-menu-spec page]]
   [:div.column
    (let [path (vec (rest path))]
      (match [path]
        [[:membership]] [membership/membership]
        [[:contact]] [:h1 "TODO: Implement Contact"]
        [[:payment :history]] [phistory/history]
        [[:payment :sources]] [psources/sources]
        [[:settings :change-password]] [settings/change-password]
        :else [:h1 "unmatched"]))]])


(defmethod content/view :profile [route]
  [content route])
