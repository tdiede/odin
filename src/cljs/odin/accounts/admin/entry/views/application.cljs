(ns odin.accounts.admin.entry.views.application
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [antizer.reagent :as ant]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]))



;; ==============================================================================
;; approval ---------------------------------------------------------------------
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
;; cards ------------------------------------------------------------------------
;; ==============================================================================


(def ^:private fitness-headers
  {:interested   "Please tell the members why you want to join their community."
   :free_time    "What do you like to do in your free time?"
   :dealbreakers "Do you have any dealbreakers?"
   :experience   "Describe your past experience(s) living in shared spaces."
   :skills       "How will you contribute to the community?"
   :conflicts    "Please describe how you would resolve a conflict between yourself and another member of the home."})


(defn community-fitness-card
  "Community fitness questionnaire answers."
  [{fitness :fitness}]
  (let [ks [:interested :free_time :dealbreakers :experience :skills :conflicts]]
    [ant/card
     [:p.title.is-5 "Community Fitness"]
     [ant/collapse
      (when (some? (get fitness (first ks)))
        {:default-active-key [(name (first ks))]})
      (for [k ks]
        [ant/collapse-panel
         {:header   (get fitness-headers k)
          :key      k
          :disabled (nil? (get fitness k))}
         [:p.fs1 (get fitness k)]])]]))


(defn- pet-content
  [{:keys [type breed weight vaccines sterile bitten demeanor daytime_care]}]
  [:div
   [:div.columns
    [:div.column
     [:p [:b "Type"]]
     [:p (name type)]]
    [:div.column
     [:p [:b "Breed"]]
     [:p breed]]
    [:div.column
     [:p [:b "Weight"]]
     [:p (str weight "lbs")]]]

   [:div.columns
    [:div.column
     [:p [:b "Vaccinated?"]]
     [ant/checkbox {:disabled true :checked vaccines}]]
    [:div.column
     [:p [:b "Sterile?"]]
     [ant/checkbox {:disabled true :checked sterile}]]
    [:div.column
     [:p [:b "Bitey?"]]
     [ant/checkbox {:disabled true :checked bitten}]]]

   [:div.columns
    [:div.column
     [:p [:b "Demeanor"]]
     [:p (or demeanor "N/A")]]
    [:div.column
     [:p [:b "Daytime care"]]
     [:p (or daytime_care "N/A")]]]])


(defn pet-card
  [{:keys [has_pet pet] :as application}]
  [ant/card
   [:p.title.is-5 "Pet Info"]
   (cond
     (nil? has_pet)   [:p "Unanswered"]
     (false? has_pet) [:p "No pet."]
     :otherwise       (pet-content pet))])


(defn- income-file [{:keys [id uri name]}]
  [:li [:a {:href uri :download name} name]])


(defn overview-card
  "An high-level overview of the member application."
  [account {:keys [communities move_in term fitness pet income] :as application}]
  [ant/card
   [:p.title.is-5 "Overview"]
   [approve-modal account application]

   ;; communites, move-in date
   [:div.columns
    [:div.column
     [:p [:b "Desired communities"]]
     [:p (if (empty? communities)
           "N/A"
           (->> (for [c communities]
                  [:a {:href ""} (:name c)])
                (interpose ", ")
                (into [:span])))]]
    [:div.column
     [:p [:b "Ideal move-in date"]]
     [:p (if (nil? move_in) "N/A" (format/date-short move_in))]]]

   ;; length of stay, income
   [:div.columns
    [:div.column
     [:p [:b "Desired length of stay"]]
     [:p (if (nil? term) "N/A" (str term " months"))]]
    [:div.column
     [:p [:b "Income Files"]]
     (if (empty? income)
       "N/A"
       [:ul
        (map-indexed #(with-meta (income-file %2) {:key %1}) income)])]]

   [:div
    (cond
      (= :applicant (:role account))
      [ant/button
       {:size     :large
        :type     :primary
        :disabled (not= :submitted (:status application))
        :on-click #(dispatch [:modal/show :admin.accounts.approval/modal])}
       "Approve"]

      (= :approved (:status application))
      [:p "Approved by "
       [:b (get-in application [:approved_by :name])]
       " at "
       (format/date-time-short (:approved_at application))]

      :otherwise [:div])]])
