(ns odin.profile.payments.history.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [odin.components.subnav :refer [subnav]]))


(defmethod content/view :profile/payment-history [route]
  [:div.columns
   [:div.column.is-one-quarter
    [subnav [["History" :profile/payment-history]
             ["Sources" :profile/payment-sources]]]]
   [:div.column
    [:h1 "Payment History"]]])


; [ant/breadcrumb {:separator "⟩"}
;  [ant/breadcrumb-item [:a {:href "/"} [:span.icon.is-small [:i.fa.fa-home]]]]
;  [ant/breadcrumb-item [:a {:href "/accounts"} "Accounts"]]
;  [ant/breadcrumb-item [:span "Josh Lehman"]]]
