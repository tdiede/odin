(ns odin.accounts.admin.entry.views.actions
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]))


;; ==============================================================================
;; Modals -----------------------------------------------------------------------
;; ==============================================================================


(def approve-modal-name
  :admin.accounts.approval/modal)


(def ^:private terms
  [3 6 12])


(defn approve-modal
  [account application]
  (let [is-showing   (subscribe [:modal/visible? :admin.accounts.approval/modal])
        units        (subscribe [:admin.accounts.entry.approval/units])
        is-approving (subscribe [:loading? :admin.accounts.entry/approve])
        form         (r/atom {:community (when (= 1 (count (:communities application)))
                                           (-> application :communities first :id))
                              :term      (:term application)
                              :move-in   (:move_in application)})]
    (r/create-class
     {:component-will-mount
      (fn [_]
        (when-some [p (:community @form)]
          (dispatch [:admin.accounts.entry.approval/fetch-units p])))
      :reagent-render
      (fn [account]
        [ant/modal
         {:title     (str "Approve " (:name account))
          :visible   @is-showing
          :on-cancel #(dispatch [:modal/hide :admin.accounts.approval/modal])
          :footer    [(r/as-element
                       ^{:key 1}
                       [ant/button
                        {:on-click #(dispatch [:modal/hide :admin.accounts.approval/modal])
                         :size     :large}
                        "Cancel"])
                      (r/as-element
                       ^{:key 2}
                       [ant/button
                        {:disabled (-> @form :unit nil?)
                         :size     :large
                         :type     :primary
                         :loading  @is-approving
                         :on-click #(dispatch [:admin.accounts.entry/approve (:id application) @form])}
                        "Approve"])]}
         ;; Choose community
         [ant/form-item {:label "Approve for which community?"}
          [ant/radio-group
           {:on-change #(let [property-id (.. % -target -value)]
                          (swap! form assoc :community property-id)
                          (dispatch [:admin.accounts.entry.approval/fetch-units property-id]))
            :value     (:community @form)}
           (map-indexed
            (fn [i {:keys [id name]}]
              ^{:key i} [ant/radio {:value id} name])
            (:communities application))]]

         ;; Choose term
         (when (:community @form)
           [ant/form-item {:label "For which term?"}
            [ant/radio-group
             {:on-change #(swap! form assoc :term (.. % -target -value))
              :value     (:term @form)}
             (map
              (fn [t]
                ^{:key t} [ant/radio {:value t} (str t " month")])
              terms)]])

         ;; Choose move-in date
         (when (:community @form)
           [ant/form-item {:label (format/format "When will %s move in?" (:name account))}
            [ant/date-picker
             {:value       (js/moment. (:move-in @form))
              :allow-clear false
              :on-change   #(swap! form assoc :move-in (.toDate %))}]])

         ;; Chose unit
         (when (:community @form)
           [ant/form-item {:label (format/format "Which unit will %s live in?" (:name account))}
            [ant/select
             {:value       (str (:unit @form))
              :placeholder "Select unit"
              :on-change   #(swap! form assoc :unit (tb/str->int %))
              :style       {:width "100%"}}
             (doall
              (map
               (fn [{:keys [id number occupant]}]
                 ^{:key id}
                 [ant/select-option {:value (str id)}
                  (if (some? occupant)
                    (format/format "Unit #%s (occupied by %s until %s)"
                                   number (:name occupant) (-> occupant :active_license :ends format/date-short))
                    (str "Unit #" number))])
               @units))]])])})))


;; ==============================================================================
;; API --------------------------------------------------------------------------
;; ==============================================================================


(defmulti actions :role)


(defmethod actions :default [_] [:div])


(defn- applicant-actions [account]
  (let [application (subscribe [:account/application (:id account)])]
    [:div
     [approve-modal account @application]
     [ant/affix {:offset-bottom 0
                 :style         {:position "absolute"
                                 :bottom   20
                                 :right    0}}
      [ant/button {:size     :large
                   :type     :primary
                   :disabled (not= :submitted (:status @application))
                   :on-click #(dispatch [:modal/show :admin.accounts.approval/modal])}
       "Approve"]]]))


(defmethod actions :applicant [account]
  [applicant-actions account])
