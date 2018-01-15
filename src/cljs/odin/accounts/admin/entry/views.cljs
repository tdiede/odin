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


(defn- most-current-license [account]
  (or (tb/find-by (comp #{:active} :status) (:licenses account))
      (first (:licenses account))))


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


(defmethod subheader :member [account]
  (let [{:keys [status property unit]} (most-current-license account)]
    [:span
     (if (= status :active) "Lives" [:i "Lived"])
     " in " [:a {:href ""} (:name property)]
     " in room #"
     [:b (:number unit)]]))


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
  (for [[label icon-name enabled tooltip opts] icon-specs]
    ^{:key icon-name}
    [:div.level-item.has-text-centered
     [:div
      [:p.heading label]
      [ant/tooltip {:title tooltip}
       (->> (cond
              (some? opts) opts
              enabled      status-icon-on
              :otherwise   status-icon-off)
            (status-icon icon-name))]]]))


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
        rent-status    (:rent_status (most-current-license account))
        deposit-status (get-in account [:deposit :status])]
    [ant/card
     [:div.level.is-mobile
      (status-icons
       ["rent" "fa-home" (= rent-status :paid) (rent-tooltip rent-status) (rent-style rent-status)]
       ["deposit" "fa-shield" (= deposit-status :paid) (deposit-tooltip deposit-status)
        (deposit-style deposit-status)]
       ["autopay" "fa-refresh" @autopay-on (if @autopay-on "Autopay is on." "Autopay is NOT on.")]
       ["bank account" "fa-university" @has-bank (if @has-bank "Bank account is linked." "No bank account linked.")]
       ["credit card" "fa-credit-card" @has-card (if @has-card "A credit/debit card is linked." "No credit/debit cards linked.")])]]))


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
     [payments-ui/payments-table @payments @is-loading
      :columns (conj payments-ui/default-columns :add-check :method)]]))


(defn payments-view [account]
  (let [payments  (subscribe [:payments/by-account-id (:id account)])
        modal-key :admin.accounts.entry/add-check-modal]
    [:div.columns
     [:div.column
      [:div.columns
       [:div.column
        [:p.title.is-5 "Payments"]]
       [:div.column.has-text-right
        [payments-ui/add-check-modal modal-key @payments
         :on-submit #(if (= "new" (:payment %))
                       (dispatch [:admin.accounts.entry/add-payment! modal-key (:id account) %])
                       (dispatch [:admin.accounts.entry/add-check! modal-key %]))]
        [ant/button
         {:type     :dashed
          :on-click #(dispatch [:modal/show modal-key])
          :icon     "plus"}
         "Add Check"]]]
      [payments-table account]]]))


;; notes ========================================================================


(defn notes-view [account]
  (let [notes (subscribe [:admin.accounts.entry/notes])]
    [:div.columns
     [:div.column
      [:div.mb2 [notes/new-note-form account]]
      (doall
       (map-indexed
        #(with-meta [notes/note-card %2] {:key %1})
        @notes))
      (when-not (empty? @notes)
        [notes/pagination])]]))


;; membership ===================================================================


(defn- reassign-unit-option
  [{:keys [id code number occupant] :as unit}]
  [ant/select-option {:value (str id)}
   (if (some? occupant)
     (format/format "Unit #%d (occupied by %s until %s)"
                    number
                    (:name occupant)
                    (-> occupant :active_license :ends format/date-short))
     (str "Unit #" number))])


(defn- reassign-modal-footer
  [account {:keys [rate unit] :as form}]
  (let [is-loading (subscribe [:loading? :admin.accounts.entry/reassign!])
        license-id (get-in account [:active_license :id])]
    [:div
     [ant/button
      {:size     :large
       :on-click #(dispatch [:modal/hide db/reassign-modal-key])}
      "Cancel"]
     [ant/button
      {:type     :primary
       :size     :large
       :disabled (or (nil? rate) (nil? unit))
       :loading  @is-loading
       :on-click #(dispatch [:admin.accounts.entry/reassign! license-id form])}
      "Reassign"]]))


(defn- reassign-modal [account]
  (let [is-visible    (subscribe [:modal/visible? db/reassign-modal-key])
        units-loading (subscribe [:loading? :property/fetch])
        rate-loading  (subscribe [:loading? :admin.accounts.entry.reassign/fetch-rate])
        units         (subscribe [:property/units (get-in account [:property :id])])
        form          (subscribe [:admin.accounts.entry.reassign/form-data])
        license       (:active_license account)]
    [ant/modal
     {:title     (str "Reassign " (:name account))
      :visible   @is-visible
      :on-cancel #(dispatch [:modal/hide db/reassign-modal-key])
      :footer    (r/as-element [reassign-modal-footer account @form])}

     ;; unit selection
     [ant/form-item {:label "Which unit?"}
      (if @units-loading
        [:div.has-text-centered
         [ant/spin {:tip "Fetching units..."}]]
        [ant/select
         {:style     {:width "100%"}
          :value     (str (:unit @form))
          :on-change #(dispatch [:admin.accounts.entry.reassign/select-unit % (:term license)])}
         (doall
          (map-indexed
           #(with-meta (reassign-unit-option %2) {:key %1})
           @units))])]

     ;; rate selection
     [ant/form-item
      {:label "What should his/her rate change to?"}
      (if @rate-loading
        [:div.has-text-centered
         [ant/spin {:tip "Fetching current rate..."}]]
        [ant/input-number
         {:style     {:width "100%"}
          :value     (:rate @form)
          :disabled  (nil? (:unit @form))
          :on-change #(dispatch [:admin.accounts.entry.reassign/update :rate %])}])]]))


(defn membership-actions [account]
  [:div
   [reassign-modal account]
   [ant/button
    {:icon     "swap"
     :on-click #(dispatch [:admin.accounts.entry.reassign/show account])}
    "Reassign"]])


(defn membership-view [account]
  (let [license   (most-current-license account)
        is-active (= :active (:status license))]
    [:div.columns
     [:div.column
      [membership/license-summary license
       (when is-active {:content [membership-actions account]})]]
     [:div.column
      (when is-active [status-bar account])]]))


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
      ["membership" "payments" "application" "notes"])]))


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
         "payments"    [payments-view account]
         "application" [application-view account]
         "notes"       [notes-view account]
         [:div])])))
