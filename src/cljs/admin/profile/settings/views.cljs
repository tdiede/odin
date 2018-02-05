(ns admin.profile.settings.views
  (:require [antizer.reagent :as ant]
            [iface.form :as form]
            [iface.typography :as typography]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]))

(def placeholder "••••••••")


(def ^:private form-style
  {:label-col   {:span 6}
   :wrapper-col {:span 10}})


(defn- form-item-props [label]
  (merge form-style {:label label}))


(defn- matching-passwords [form-data]
  (let [form (ant/get-form)]
    (fn [rule _ cb]
      (let [{:keys [new-password-1 new-password-2]} @form-data]
        (if (and (some? new-password-1)
                 (some? new-password-2)
                 (not= new-password-1 new-password-2))
          (cb "Your passwords do not match.")
          (cb))))))


(defn- new-passwords [form-data]
  (let [form (ant/get-form)]
    (fn [_ _ cb]
      (let [{:keys [old-password new-password-1]} @form-data]
        (if (and (some? old-password)
                 (some? new-password-1)
                 (= old-password new-password-1))
          (cb "Your new password must be different than your current one.")
          (cb))))))


(defn- form-items [form-data]
  [{:key             :old-password
    :form-item-props (form-item-props "Current Password")
    :ant-id          "current-password"
    :rules           [{:required true
                       :message  "Your current password is required."}
                      {:validator (new-passwords form-data)}]
    :input-props     {:placeholder placeholder
                      :type        "password"}}
   {:key             :new-password-1
    :form-item-props (form-item-props "New Password")
    :ant-id          "new-password-1"
    :rules           [{:required true
                       :message  "Please input a new password."}
                      {:min     8
                       :message "Your new password must be at least 8 characters long."}
                      {:validator (new-passwords form-data)}
                      {:validator (matching-passwords form-data)}]
    :input-props     {:placeholder placeholder
                      :type        "password"}}
   {:key             :new-password-2
    :form-item-props (form-item-props "Confirm Password")
    :ant-id          "new-password-2"
    :rules           [{:required true
                       :message  "Please confirm your password."}
                      {:validator (matching-passwords form-data)}]
    :input-props     {:placeholder placeholder
                      :type        "password"}}])


(defn- on-submit [form-data]
  (fn [errors _]
    (when (nil? errors)
      (dispatch [:account/change-password! @form-data]))))


(defn change-password-form []
  (let [form-data  (r/atom {})
        on-change  (fn [k] #(swap! form-data assoc k (.. % -target -value)))
        submitting (subscribe [:ui/loading? :account/change-password!])]
    (fn []
      (let [form (ant/get-form)]
        [ant/form {:layout    "horizontal"
                   :on-submit #(do
                                 (.preventDefault %)
                                 (ant/validate-fields form (on-submit form-data)))}
         (form/items (form-items form-data) :on-change on-change)
         [ant/form-item (merge form-style {:label " "})
          [ant/button
           {:html-type "submit"
            :type      :primary
            :loading   @submitting}
           "Change Password"]]]))))


(defn change-password []
  [:div
   (typography/view-header "Change Password")
   [:div.columns
    [:div.column.is-8
     [ant/card
      (r/as-element (ant/create-form (change-password-form)))]]]])
