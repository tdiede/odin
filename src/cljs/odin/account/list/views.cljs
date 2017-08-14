(ns odin.account.list.views
  (:require [odin.content :as content]
            [odin.routes :as routes]
            [odin.l10n :as l10n]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]
            [odin.utils.formatters :as format]))


(defn comp-alphabetical [key]
  (let [key (name key)]
    (fn [a b]
      (compare (aget a key) (aget b key)))))


(def ^:private columns
  [{:title     "First Name"
    :dataIndex :first_name
    :sorter    (comp-alphabetical :first_name)}
   {:title     "Last Name"
    :dataIndex :last_name
    :sorter    (comp-alphabetical :last_name)}
   {:title     "Email"
    :dataIndex :email
    :sorter    (comp-alphabetical :email)
    :render    (fn [val item _]
                 (r/as-element
                  [:a {:href (routes/path-for :account/entry
                                              :account-id (aget item "id"))}
                   val]))}
   {:title     "Phone"
    :dataIndex :phone
    :sorter    (comp-alphabetical :phone)
    :render    (fn [val]
                 (format/phone-number val))}])


(defn account->column [key account]
  (assoc account :key key))


(defn accounts-table []
  (let [accounts (subscribe [:accounts/list])
        loading  (subscribe [:accounts.list/loading?])]
    [ant/table
     {:loading    @loading
      :columns    columns
      :dataSource (map-indexed account->column @accounts)}]))

(defn accounts-recently-viewed []
  [ant/card {:title (r/as-element [:span
                                   [:span.icon.is-small [:i.fa.fa-clock-o]]
                                   "Recently Viewed"])}
   [:ul
    [:li [:a {:href ""} "Josh Lehman"]]
    [:li [:a {:href ""} "Derryl Carter"]]]])


(defn view []
  [:div.page
   [:div.flexrow
    [:h1 (l10n/translate :accounts)]
    [ant/input-search {:class "page-level-search valign flex-pin-right"}]]
   [:div.columns
    [:div.column.is-three-quarters
     [ant/card ;;{:title "Accounts"}
      ; [ant/input-search]
      ; [ant/button
      ;  {:on-click #(dispatch [:account/change-random-phone!])
      ;   :style    {:margin-bottom 24}}
      ;  "Change Random Phone"]
      [accounts-table]]]
    [:div.column
     [accounts-recently-viewed]]]])


(defmethod content/view :account/list [route]
  [view])
