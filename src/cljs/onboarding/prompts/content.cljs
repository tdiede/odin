(ns onboarding.prompts.content
  (:require [antizer.reagent :as ant]
            [re-frame.core :refer [subscribe]]))

(defmulti content :keypath)

(defmethod content :default
  [{:keys [keypath]}]
  [:p "No content defined for " [:b (str keypath)]])

(defmethod content :overview/start
  [_]
  [:div.content
   [:p "We're thrilled that you're joining the community and can't wait to get to know you better. Each member here is integral to the health and prosperity of our community" [:span {:dangerouslySetInnerHTML {:__html "&mdash;"}}] "we firmly believe that the whole is greater than the sum of its parts."]

   [:p "We remove the hassles from your home life experience and want to make sure your move-in process is as easy and convenient as possible. The following steps will introduce you to the premium services we offer to help you with moving and getting settled in."]

   [:p "Complete each prompt using the blue " [:b "Continue"] " button in the bottom right corner of the screen, or jump around using the menu at left. After you have completed all of the prompts you'll officially become a Starcity member."]

   [:p "Let's get started!"]])

(defmulti next-button :keypath)

(defmethod next-button :default [prompt]
  (let [dirty       (subscribe [:prompt/dirty?])
        can-advance (subscribe [:prompt/can-advance?])
        is-saving   (subscribe [:prompt/saving?])]
    [ant/button {:type      :primary
                 :size      :large
                 :disabled  (not @can-advance)
                 :loading   @is-saving
                 :html-type :submit}
     (if @dirty
       [:span {:dangerouslySetInnerHTML {:__html "Save &amp; Continue"}}]
       "Continue")]))
