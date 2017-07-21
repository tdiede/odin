(ns admin.accounts.views
  (:require [admin.views.content :as content]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]))


(defn comp-alphabetical [key]
  (let [key (name key)]
    (fn [a b]
      (compare (aget a key) (aget b key)))))


(def ^:private columns
  [{:title     "First Name"
    :dataIndex :account/first-name
    :sorter    (comp-alphabetical :first-name)}
   {:title     "Last Name"
    :dataIndex :account/last-name
    :sorter    (comp-alphabetical :last-name)}
   {:title     "Email"
    :dataIndex :account/email
    :sorter    (comp-alphabetical :email)}])


(defn account->column [key account]
  (assoc account :key key))


(defn accounts-table []
  (let [accounts (subscribe [:accounts/list])
        loading  (subscribe [:accounts.list/loading?])]
    [ant/table
     {:loading    @loading
      :columns    columns
      :dataSource (map-indexed account->column @accounts)}]))


(defmethod content/view :accounts [route]
  [ant/card {:title "Accounts"}
   [accounts-table]])
