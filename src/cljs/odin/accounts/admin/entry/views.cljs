(ns odin.accounts.admin.entry.views
  (:require [odin.accounts.admin.entry.db :as db]
            [odin.accounts.admin.entry.views.application :as application]
            [odin.accounts.admin.entry.views.notes :as notes]
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


(defn application-view [account]
  (let [{:keys [fitness has_pet pet] :as application}
        @(subscribe [:account/application (:id account)])]
    [:div.columns
     [:div.column
      [application/overview-card account application]
      [application/pet-card application]]
     [:div.column
      [application/community-fitness-card application]]]))


;; payments =====================================================================


(defn payments-table [account]
  (let [payments   (subscribe [:payments/by-account-id (:id account)])
        is-loading (subscribe [:loading? :payments/fetch])]
    [ant/card {:class "is-flush"}
     [payments-ui/payments-table @payments @is-loading]]))


;; notes ========================================================================


(defn notes-view [account]
  (let [notes (subscribe [:admin.accounts.entry/notes])]
    [:div.columns
     [:div.column.is-one-third
      [notes/new-note-form account]]
     [:div.column
      (doall
       (map-indexed
        #(with-meta [notes/note-card %2] {:key %1})
        @notes))
      (when-not (empty? @notes)
        [notes/pagination])]]))


;; membership ===================================================================


(defn membership-view [account]
  [:div.columns
   [:div.column
    [membership/license-summary (:active_license account)
     {:content [status-bar account]}]]
   [:div.column]])


;; ==============================================================================
;; layout -----------------------------------------------------------------------
;; ==============================================================================


(defn- menu-item [role key]
  [ant/menu-item
   {:key key :disabled (not (db/allowed? role key))}
   (string/capitalize key)])


(defn menu [{role :role}]
  (let [selected (subscribe [:admin.accounts.entry/selected-tab])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:admin.accounts.entry/select-tab (aget % "key")])}
     (map
      (partial menu-item role)
      ["membership" "application" "notes"])]))


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
         [menu account]]]

       (case @selected
         "membership"  [membership-view account]
         "application" [application-view account]
         "notes"       [notes-view account]
         [:div])])))
