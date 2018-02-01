(ns onboarding.prompts.services.storage
  (:require [onboarding.components.catalogue :as catalogue]
            [onboarding.prompts.content :as content]))

(def ^:private description
  ["Remove the clutter from your room and use our storage service. We'll provide
  you with bins that you can access anytime you'd like&mdash;just request the
  bins and we'll deliver them and pick them up the next day."
   "<i>We won't charge you for anything until after you've moved in and provided your items to be stored.</i>"])

(defmethod content/content :services/storage
  [{:keys [keypath data] :as item}]
  (let [{:keys [orders catalogue]} data]
    [:div.content
     (map-indexed #(with-meta [:p {:dangerouslySetInnerHTML {:__html %2}}] {:key %1}) description)
     [catalogue/grid keypath catalogue orders]]))
