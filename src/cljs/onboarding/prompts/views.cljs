(ns onboarding.prompts.views
  (:require [antizer.reagent :as ant]
            [onboarding.prompts.content :as content]
            [onboarding.prompts.admin.emergency]
            [onboarding.prompts.services.moving]
            [onboarding.prompts.services.storage]
            [onboarding.prompts.services.cleaning]
            [onboarding.prompts.services.upgrades]
            [onboarding.prompts.deposit.method]
            [onboarding.prompts.deposit.bank]
            [onboarding.prompts.deposit.verify]
            [onboarding.prompts.deposit.pay]
            [onboarding.prompts.review]
            [re-frame.core :refer [dispatch subscribe]]
            [onboarding.db :as db]))

(def ^:private advisor-image
  [:img.is-circular
   {:src   "/assets/images/meg.jpg"
    :alt   "community advisor headshot"
    :class "community-advisor"}])

(defn prompt-header []
  (let [title (subscribe [:prompt/title])]
    [:header
     [:figure.image.is-64x64
      [:a {:on-click #(dispatch [:help/toggle])} advisor-image]]
     [:h3.prompt-title.title.is-4 @title]]))

(defn- previous-button [active]
  [ant/button {:type      :ghost
               :size      :large
               :icon      :left
               :html-type :button
               :on-click  #(dispatch [:prompt/previous (:keypath active)])}
   "Previous"])

(defn- save-button []
  (let [dirty     (subscribe [:prompt/dirty?])
        is-saving (subscribe [:prompt/saving?])
        prompt    (subscribe [:prompt/active])]
    [ant/button {:type     :ghost
                 :size     :large
                 :style    {:margin-right 5}
                 :disabled (not @dirty)
                 :loading  @is-saving
                 :on-click #(dispatch [:prompt/save (:keypath @prompt) (:data @prompt) {:nav false}])}
     "Save"]))

(defn prompt-footer [active]
  (let [has-previous (subscribe [:prompt/has-previous?])
        can-save     (subscribe [:prompt/can-save?])]
    [:div.columns.is-mobile.prompt-controls
     (when @has-previous
       [:div.column.has-text-left
        [previous-button active]])
     [:div.column
      [:div.is-pulled-right
       (when @can-save
         [save-button])
       [content/next-button active]]]]))

(defn prompt []
  (let [active (subscribe [:prompt/active])]
    [:form
     {:on-submit #(do
                    (.preventDefault %)
                    (dispatch [:prompt/continue (:keypath @active)]))}
     [:div.prompt
      [prompt-header]
      [:div.prompt-content
       (content/content @active)]
      [prompt-footer @active]]]))
