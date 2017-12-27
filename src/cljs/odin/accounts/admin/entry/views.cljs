(ns odin.accounts.admin.entry.views
  (:require [odin.accounts.admin.entry.views.actions :as actions]
            [odin.utils.formatters :as format]
            [re-frame.core :refer [subscribe dispatch]]
            [toolbelt.core :as tb]
            [iface.loading :as loading]
            [iface.typography :as typography]
            [antizer.reagent :as ant]
            [odin.components.payments :as payments-ui]
            [odin.components.membership :as membership]))



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
  {:class "text-grey" :style {:fontSize "24px"}})


(def status-icon-on
  {:class "text-blue" :style {:fontSize "24px"}})


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
      (assoc :style {:fontSize "24px"})))


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
      (assoc :style {:fontSize "24px"})))


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
    (tb/log application)
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
           [:p (get fitness k)]])]]]]))


;; payments =====================================================================


(defn payments-table [account]
  (let [payments   (subscribe [:payments/by-account-id (:id account)])
        is-loading (subscribe [:loading? :payments/fetch])]
    [ant/card {:class "is-flush"}
     [payments-ui/payments-table @payments @is-loading]]))


;; ==============================================================================
;; layout -----------------------------------------------------------------------
;; ==============================================================================


(defmulti layout :role)


(defmethod layout :default [{role :role}]
  [:div "nothing to see for role " role])


(defmethod layout :applicant [account]
  [:div.columns
   [:div.column
    [application-info account]]
   [:div.column
    ]])


(defmethod layout :member [account]
  [:div.columns
   [:div.column
    [status-bar account]
    [membership/license-summary (:active_license account)]
    [:div.mt3
     [:p.title.is-5 "Payments"]
     [payments-table account]]]

   [:div.column]])


(defn view [{{account-id :account-id} :params}]
  (let [{:keys [email phone] :as account} @(subscribe [:account (tb/str->int account-id)])
        is-loading                        (subscribe [:loading? :account/fetch])]
    (if (or @is-loading (nil? account))
      (loading/fullpage :text "Fetching account...")
      [:div
       [:div.columns
        [:div.column.is-three-quarters
         (typography/view-header (:name account) (subheader account))]
        [:div.column [contact-info account]]]

       (actions/actions account)
       (layout account)])))
