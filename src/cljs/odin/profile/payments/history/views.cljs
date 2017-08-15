(ns odin.profile.payments.history.views
  (:require [odin.views.content :as content]
            [odin.routes :as routes]
            [odin.components.subnav :refer [subnav]]))


(defmethod content/view :profile/payment-history [route]
  [:div.columns
   [:div.column.is-2
    [subnav [["History" :profile/payment-history]
             ["Sources" :profile/payment-sources]]]]
   [:div.column.highlight-full
    [:h1 "Payment History"]]])



; [ant/breadcrumb {:separator "⟩"}
;  [ant/breadcrumb-item [:a {:href "/"} [:span.icon.is-small [:i.fa.fa-home]]]]
;  [ant/breadcrumb-item [:a {:href "/accounts"} "Accounts"]]
;  [ant/breadcrumb-item [:span "Josh Lehman"]]]
