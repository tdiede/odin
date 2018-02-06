(ns onboarding.prompts.services.moving
  (:require [antizer.reagent :as ant]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]
            [cljsjs.moment]))

(defn- form
  [keypath commencement {:keys [needed date time]}]
  (let [commencement (js/moment. commencement)]
    [ant/card
     [:div.field
      [:label.label "Do you need help moving in?"]
      [:div.control
       [ant/radio-group
        {:on-change #(dispatch [:prompt/update keypath :needed (= (.. % -target -value) "yes")])
         :value     (cond (true? needed) "yes" (false? needed) "no" :otherwise nil)}
        [ant/radio {:value "yes"} "Yes"]
        [ant/radio {:value "no"} "No"]]]]
     (when needed
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
   [:p "Starcity provides moving services to assist you with the lugging and lifting at Starcity on move-in day. " [:strong "Moving services are $50 per hour with a 2 hour minimum."]]
   [form keypath commencement data]])
