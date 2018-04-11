(ns onboarding.prompts.services.moving
  (:require [antizer.reagent :as ant]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]
            [cljsjs.moment]))

(defn- form
  [keypath commencement {:keys [furniture mattress assistance date time]}]
  (let [commencement (js/moment. commencement)]
    [ant/card
     [:div.field
      [:label.label "Are you bringing your own furniture (in addition to a mattress)?"]
      [:div.control
       [ant/radio-group
        {:on-change #(dispatch [:prompt/update keypath :furniture (= (.. % -target -value) "yes")])
         :value     (cond (true? furniture) "yes" (false? furniture) "no" :otherwise nil)}
        [ant/radio {:value "yes"} "Yes"]
        [ant/radio {:value "no"} "No"]]]]


     (when (false? furniture)
       [:div.field
        [:label.label "Are you bringing your own mattress?"]
        [:div.control
         [ant/radio-group
          {:on-change #(dispatch [:prompt/update keypath :mattress (= (.. % -target -value) "yes")])
           :value     (cond (true? mattress) "yes" (false? mattress) "no" :otherwise nil)}
          [ant/radio {:value "yes"} "Yes"]
          [ant/radio {:value "no"} "No"]]]])

     (when (and (false? furniture) (false? mattress))
       [:div.field
        [:label.label "Do you need help moving in?"]
        [:div.control
         [ant/radio-group
          {:on-change #(dispatch [:prompt/update keypath :assistance (= (.. % -target -value) "yes")])
           :value     (cond (true? assistance) "yes" (false? assistance) "no" :otherwise nil)}
          [ant/radio {:value "yes"} "Yes"]
          [ant/radio {:value "no"} "No"]]]])

     (when (or (true? assistance) (true? furniture) (true? mattress))
       [:div
        [:div.field
         [:label.label "What date will you be moving in on?"]
         [:div.control
          [ant/date-picker
           {:value         (js/moment. date)
            :on-change     #(dispatch [:prompt/update keypath :date (.toDate %)])
            :disabled-date #(.isBefore % commencement)
            :allow-clear   false
            :format        "MM-DD-YYYY"}]]]
        [:div.field
         [:label.label "At what time will you be moving in?"]
         [:div.control
          [ant/time-picker
           {:value                 (when time (js/moment. time))
            :on-change             #(dispatch [:prompt/update keypath :time (.toDate %)])
            :format                "h:mm A"
            :use12Hours            true
            :disabled-minutes      (fn [] (range 1 61))
            :hide-disabled-options true}]]]])]))


(defmethod content/content :services/moving
  [{:keys [keypath commencement data] :as item}]
  [:div.content
   [:p "Your room already comes furnished, but if you want to bring all of your own stuff, we'll clear your room, store the existing furniture, and expertly move your furniture in for you (trust us; you don't want to have to worry about messing up your new room on move-in day)."]
   [:p "Bringing your own furniture will incur a one-time charge of " [:b "$500"] "."]
   [:p "If you're " [:i "only bringing a mattress"] ", that's " [:b "$200"] "."]
   [:p "If you just need help moving your belongings into the room, we can help at a rate of " [:b "$50/hour"] "."]
   [form keypath commencement data]])
