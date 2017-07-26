(ns odin.account.list.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [reagent.core :as r]))


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
    :sorter    (comp-alphabetical :phone)}])


(defn account->column [key account]
  (assoc account :key key))


(defn accounts-table []
  (let [accounts (subscribe [:accounts/list])
        loading  (subscribe [:accounts.list/loading?])]
    [ant/table
     {:loading    @loading
      :columns    columns
      :dataSource (map-indexed account->column @accounts)}]))


(defmethod content/view :account/list [route]
  [ant/card {:title "Accounts"}
   [ant/button
    {:on-click #(dispatch [:account/change-random-phone!])
     :style    {:margin-bottom 24}}
    "Change Random Phone"]
   [accounts-table]])
