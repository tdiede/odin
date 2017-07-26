(ns odin.account.entry.views
  (:require [odin.views.content :as content]
            [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]))


(defn account-view [account-id]
  (let [account (subscribe [:account/entry account-id])]
    [:div
     (str @account)]))


(defmethod content/view :account/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [ant/card {:title "Accounts Entry"}
     [ant/button {:on-click #(dispatch [:account/fetch account-id])}
      "Refresh"]
     [account-view account-id]]))
