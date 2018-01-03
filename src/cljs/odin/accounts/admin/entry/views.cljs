(ns odin.accounts.admin.entry.views
  (:require [odin.accounts.admin.entry.views.actions :as actions]
            [odin.utils.formatters :as format]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [iface.loading :as loading]
            [iface.typography :as typography]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.components.membership :as membership]
            [reagent.core :as r]
            [clojure.string :as string]))


;; ==============================================================================
;; components -------------------------------------------------------------------
;; ==============================================================================


;; subheader ====================================================================


(defmulti subheader :role)


(defmethod subheader :default [{:keys [role]}]
  [:b role])


(defmethod subheader :applicant [{:keys [application]}]
  [:span "Began his/her application on "
   [:b (format/date-short (:created application))]
   " and was last active at "
   [:b (format/date-time (:updated application))]])


(defmethod subheader :member
  [{{unit :unit} :active_license, property :property}]
  [:span "Lives in " [:a {:href ""} (:name property)] " in room #" [:b (:number unit)]])


;; contact info =================================================================


(defn contact-info [{:keys [email phone dob]}]
  [:div
   [:p.has-text-right.fs1
    [:a {:href (str "mailto:" email)} email]
    [ant/icon {:class "ml1" :type "mail"}]]
   (when-some [p phone]
     [:p.has-text-right.fs1
      (format/phone-number p)
      [ant/icon {:class "ml1" :type "phone"}]])
   (when-some [d dob]
     [:p.has-text-right.fs1
      (format/date-month-day d)
      [ant/icon {:class "ml1" :type "gift"}]])])


;; status bar ===================================================================


(def status-icon-off
  {:class "text-grey" :style {:fontSize "20px"}})


(def status-icon-on
  {:class "text-blue" :style {:fontSize "20px"}})


(defn status-icon [type {:keys [style class]}]
  [:i.fa {:class (str type " " class) :type type :style style}])


(defn status-icons [& icon-specs]
  (for [[icon-name enabled tooltip opts] icon-specs]
    ^{:key icon-name}
    [:div.level-item
     [ant/tooltip {:title tooltip}
      (->> (cond
             (some? opts) opts
             enabled      status-icon-on
             :otherwise   status-icon-off)
           (status-icon icon-name))]]))


(defn- rent-tooltip [rent-status]
  (case rent-status
    :paid    "Rent is paid."
    :due     "Rent is due."
    :overdue "Rent is overdue"
    :pending "A rent payment is pending."
    ""))


(defn- rent-style [rent-status]
  (-> (case rent-status
        :paid    {:class "text-green"}
        :due     {:class "text-yellow"}
        :overdue {:class "text-red"}
        :pending {:class "text-blue"}
        {})
      (assoc :style {:fontSize "20px"})))


(defn- deposit-tooltip [deposit-status]
  (case deposit-status
    :paid    "Deposit is paid in full."
    :partial "Deposit is partially paid."
    :overdue "Deposit is overdue."
    :unpaid  "Deposit is unpaid."
    :pending "Deposit payment(s) are pending."
    ""))


(defn- deposit-style [deposit-status]
  (-> (case deposit-status
        :paid    {:class "text-green"}
        :partial {:class "text-yellow"}
        :overdue {:class "text-red"}
        :unpaid  {:class "text-grey"}
        :pending {:class "text-blue"}
        {})
      (assoc :style {:fontSize "20px"})))


(defn status-bar [account]
  (let [autopay-on     (subscribe [:payment-sources/autopay-on? (:id account)])
        has-bank       (subscribe [:payment-sources/has-verified-bank? (:id account)])
        has-card       (subscribe [:payment-sources/has-card? (:id account)])
        rent-status    (get-in account [:active_license :rent_status])
        deposit-status (get-in account [:deposit :status])]
    [:div.level.is-mobile
     (status-icons
      ["fa-refresh" @autopay-on (if @autopay-on "Autopay is on." "Autopay is NOT on.")]
      ["fa-university" @has-bank (if @has-bank "Bank account is linked." "No bank account linked.")]
      ["fa-credit-card" @has-card (if @has-card "A credit/debit card is linked." "No credit/debit cards linked.")]
      ["fa-home" (= rent-status :paid) (rent-tooltip rent-status) (rent-style rent-status)]
      ["fa-shield" (= deposit-status :paid) (deposit-tooltip deposit-status)
       (deposit-style deposit-status)])]))


;; application info =============================================================


(def fitness-headers
  {:interested   "Please tell the members why you want to join their community."
   :free_time    "What do you like to do in your free time?"
   :dealbreakers "Do you have any dealbreakers?"
   :experience   "Describe your past experience(s) living in shared spaces."
   :skills       "How will you contribute to the community?"
   :conflicts    "Please describe how you would resolve a conflict between yourself and another member of the home."})


(defn- pet-panel-content
  [{:keys [type breed weight vaccines sterile bitten demeanor daytime_care] :as pet}]
  (tb/log pet)
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
     [:p (or demeanor "N/A")]]]
   [:div.columns
    [:div.column
     [:p [:b "Daytime care"]]
     [:p (or daytime_care "N/A")]]]])


(defn- income-file [{:keys [id uri name]}]
  [:li [:a {:href uri :download name} name]])


(defn- overview-panel
  [{:keys [communities move_in term fitness has_pet pet income] :as application}]
  [:div
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

   [:div.columns
    [:div.column
     [:p [:b "Desired length of stay"]]
     [:p (if (nil? term) "N/A" (str term " months"))]]
    [:div.column
     [:p [:b "Income Files"]]
     (if (empty? income)
       "N/A"
       [:ul
        (map-indexed #(with-meta (income-file %2) {:key %1}) income)])]]])


(defn application-info [account]
  (let [{:keys [fitness has_pet pet] :as application}
        @(subscribe [:account/application (:id account)])]
    [:div
     [:p.title.is-5 "Application"]
     [ant/card {:class "is-flush"}
      [ant/collapse {:bordered false :default-active-key ["overview"]}
       [ant/collapse-panel {:header "Overview" :key "overview"}
        [overview-panel application]]
       [ant/collapse-panel {:header   "Pet Info"
                            :key      "pet"
                            :disabled (or (empty? pet) (false? has_pet))}
        [pet-panel-content pet]]
       [ant/collapse-panel {:header   "Community fitness profile"
                            :key      "fitness"
                            :disabled (empty? fitness)}
        [ant/collapse
         (for [k [:interested :free_time :dealbreakers :experience :skills :conflicts]]
           [ant/collapse-panel {:header (get fitness-headers k) :key k :disabled (nil? (get fitness k))}
            [:p (get fitness k)]])]]]]]))


;; payments =====================================================================


(defn payments-table [account]
  (let [payments   (subscribe [:payments/by-account-id (:id account)])
        is-loading (subscribe [:loading? :payments/fetch])]
    [ant/card {:class "is-flush"}
     [payments-ui/payments-table @payments @is-loading]]))


;; notes ========================================================================


(defn- note-form
  [{:keys [subject content notify on-change is-loading disabled on-submit on-cancel
           button-text]
    :or   {on-change identity, on-submit identity, button-text "Save"}}]
  [:form {:on-submit #(do
                        (.preventDefault %)
                        (on-submit))}
   [ant/form-item {:label "Subject"}
    [ant/input {:type        :text
                :placeholder "Note subject"
                :value       subject
                :on-change   #(on-change :subject (.. % -target -value))}]]
   [ant/form-item {:label "Content"}
    [ant/input {:type        :textarea
                :rows        5
                :placeholder "Note content"
                :value       content
                :on-change   #(on-change :content (.. % -target -value))}]]
   (when (some? notify)
     [ant/form-item
      [ant/checkbox {:checked   notify
                     :on-change #(on-change :notify (.. % -target -checked))}
       "Send Slack notification"]])
   [ant/form-item
    [ant/button
     {:type      "primary"
      :html-type :submit
      :loading   is-loading
      :disabled  disabled}
     button-text]

    (when (some? on-cancel)
      [ant/button {:on-click on-cancel} "Cancel"])]])


(defn- new-note-form [account]
  (let [form        (subscribe [:admin.accounts.entry.create-note/form-data])
        can-create  (subscribe [:admin.accounts.entry/can-create-note?])
        is-creating (subscribe [:loading? :admin.accounts.entry/create-note!])]
    (fn [account]
      [ant/card {:title "New Note"}
       [note-form
        {:subject     (:subject @form)
         :content     (:content @form)
         :notify      (:notify @form)
         :on-change   (fn [k v] (dispatch [:admin.accounts.entry.create-note/update k v]))
         :is-loading  @is-creating
         :disabled    (not @can-create)
         :on-submit   #(dispatch [:admin.accounts.entry/create-note! (:id account) @form])
         :button-text "Create"}]])))


(defn- edit-note-form [note]
  (let [form      (r/atom (select-keys note [:subject :content]))
        is-saving (subscribe [:loading? :admin.accounts.entry/update-note!])]
    (fn [note]
      [ant/card {:class "note"}
       [note-form
        {:subject    (:subject @form)
         :content    (:content @form)
         :is-loading @is-saving
         :on-change  (fn [k v] (swap! form assoc k v))
         :on-cancel  #(dispatch [:admin.accounts.entry.note/toggle-editing (:id note)])
         :on-submit  #(dispatch [:admin.accounts.entry/update-note! (:id note) @form])}]])))


(defn- note [{:keys [id subject content created updated author] :as note}]
  (let [account     (subscribe [:auth])
        is-editing  (subscribe [:admin.accounts.entry.note/editing id])
        is-deleting (subscribe [:loading? :admin.accounts.entry.note/delete])]
    (fn [{:keys [id subject content created updated author] :as note}]
      (let [updated (when (not= created updated) updated)]
        (if-not @is-editing
          ;; not editing
          [ant/card {:class "note"}
           [:p.subject subject]
           [:p.byline (str "by " (:name author) " at "
                           (format/date-time-short created)
                           (when-some [d updated]
                             (str " (updated at " (format/date-time-short d) ")")))]
           [:p.body {:dangerouslySetInnerHTML {:__html (format/newlines->line-breaks content)}}]
           [:p.buttons
            (when (= (:id author) (:id @account))
              [ant/button
               {:size     :small
                :type     :ghost
                :on-click #(dispatch [:admin.accounts.entry.note/toggle-editing id])}
               "Edit"])
            (when (= (:id author) (:id @account))
              [ant/button
               {:size     :small
                :type     :danger
                :on-click (fn []
                            (ant/modal-confirm
                             {:title     "Are you sure?"
                              :content   "This cannot be undone!"
                              :on-ok     #(dispatch [:admin.accounts.entry.note/delete! id])}))}
               "Delete"])]]
          ;; is editing
          [edit-note-form note])))))


(defn- pagination []
  (let [pagination (subscribe [:admin.accounts.entry.notes/pagination])]
    [:div.mt3
     [ant/pagination
      {:show-size-changer   true
       :on-show-size-change #(dispatch [:admin.accounts.entry.notes/change-pagination %1 %2])
       :default-current     (:page @pagination)
       :total               (:total @pagination)
       :show-total          (fn [total range]
                              (format/format "%s-%s of %s notes"
                                             (first range) (second range) total))
       :page-size-options   ["5" "10" "15" "20"]
       :default-page-size   (:size @pagination)
       :on-change           #(dispatch [:admin.accounts.entry.notes/change-pagination %1 %2])}]]))


(defn- notes [account]
  (let [notes (subscribe [:admin.accounts.entry/notes])]
    [:div.columns
     [:div.column.is-one-third
      [new-note-form account]]
     [:div.column
      (doall
       (map-indexed
        #(with-meta [note %2] {:key %1})
        @notes))

      (when-not (empty? @notes)
        [pagination])]]))


;; ==============================================================================
;; layout -----------------------------------------------------------------------
;; ==============================================================================


(defn menu []
  (let [selected (subscribe [:admin.accounts.entry/selected-tab])]
    [ant/menu {:mode :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:admin.accounts.entry/select-tab (aget % "key")])}
     [ant/menu-item {:key "overview"} "Overview"]
     [ant/menu-item {:key "notes"} "Notes"]]))


(defmulti overview :role)


(defmethod overview :default [{role :role}]
  [:div "nothing to see for role " role])


(defmethod overview :applicant [account]
  [:div.columns
   [:div.column
    [application-info account]]])


(defmethod overview :member [account]
  [:div.columns
   [:div.column
    [membership/license-summary (:active_license account)
     {:content [status-bar account]}]]

   [:div.column
    [:div
     [:p.title.is-5 "Payments"]
     [payments-table account]]
    [:div.mt3
     [application-info account]]]])


(defn view [{{account-id :account-id} :params, path :path}]
  (let [{:keys [email phone] :as account} @(subscribe [:account (tb/str->int account-id)])
        selected                          (subscribe [:admin.accounts.entry/selected-tab])
        is-loading                        (subscribe [:loading? :account/fetch])]
    (if (or @is-loading (nil? account))
      (loading/fullpage :text "Fetching account...")
      [:div
       [:div.columns
        [:div.column.is-three-quarters
         (typography/view-header (:name account) (subheader account))]
        [:div.column [contact-info account]]]

       [:div.columns
        [:div.column
         [menu]]]
       (case @selected
         "overview" [overview account]
         "notes"    [notes account]
         [:div])

       (actions/actions account)])))
