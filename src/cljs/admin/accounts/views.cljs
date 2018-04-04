(ns admin.accounts.views
  (:require [admin.accounts.db :as db]
            [admin.accounts.views.application :as application]
            [admin.accounts.views.notes :as notes]
            [admin.content :as content]
            [admin.routes :as routes]
            [antizer.reagent :as ant]
            [clojure.string :as string]
            [iface.components.membership :as membership]
            [iface.components.order :as order]
            [iface.components.table :as table]
            [iface.loading :as loading]
            [iface.components.typography :as typography]
            [iface.components.payments :as payments]
            [iface.utils.formatters :as format]
            [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; list view ====================================================================
;; ==============================================================================


(defn role-menu []
  (let [selected (subscribe [:accounts.list/selected-view])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:accounts.list/select-view (aget % "key")])}
     [ant/menu-item {:key "member"} "Members"]
     [ant/menu-item {:key "applicant"} "Applicants"]
     [ant/menu-item {:key "all"} "All"]]))


(def render-name
  (table/wrap-cljs
   (fn [_ {:keys [name id]}]
     [:a {:href (routes/path-for :accounts/entry :account-id id)} name])))


(def render-email
  (table/wrap-cljs
   (fn [email {id :id}]
     [:a {:href (routes/path-for :accounts/entry :account-id id)} email])))


(def render-property
  (table/wrap-cljs
   (fn [_ {property :property}]
     [:a {:href ""} (:name property)])))


(def render-unit
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     (get-in license [:unit :code]))))


(def render-term
  (table/wrap-cljs
   (fn [_ {{:keys [term starts ends]} :active_license}]
     [ant/tooltip {:title (str (format/date-short starts) "-"
                               (format/date-short ends))}
      [:div.has-text-centered term]])))

(def render-rent-status
  (table/wrap-cljs
   (fn [_ {license :active_license}]
     [:div.has-text-right (:rent_status license "N/A")])))


(defmulti columns (fn [role _] role))


(defmethod columns :member [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :property "Community" db/params->route)
    :dataIndex :property
    :render    render-property}
   {:title     (table/sort-col-title query-params :unit "Unit" db/params->route)
    :dataIndex :unit
    :render    render-unit}
   {:title     (table/sort-col-title query-params :license_term "Term (months)" db/params->route)
    :dataIndex :term
    :render    render-term}
   {:title     "Rent Status"
    :dataIndex :rent-status
    :render    render-rent-status}])


(def render-communities
  (table/wrap-cljs
   (fn [_ account]
     (->> (for [c (get-in account [:application :communities])]
            [:small.fs1 [:a {:href ""} (:name c)]])
          (interpose ", ")
          (into [:div.has-text-right])))))


(defmethod columns :applicant [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :move_in "Move-in" db/params->route)
    :dataIndex [:application :move_in]
    :render    (table/maybe-render format/date-short)}
   {:title     (table/sort-col-title query-params :created "Started" db/params->route)
    :dataIndex [:application :created]
    :render    (table/maybe-render format/date-short)}
   {:title     (table/sort-col-title query-params :updated "Last Activity" db/params->route)
    :dataIndex [:application :updated]
    :render    (table/maybe-render format/date-time-short)}
   {:title     (table/sort-col-title query-params :submitted "Submitted" db/params->route)
    :dataIndex [:application :submitted]
    :render    (table/maybe-render format/date-time-short)}
   {:title     "Communities"
    :dataIndex [:application :communities]
    :render    render-communities}])


(def render-role
  (table/wrap-cljs
   (fn [role {:keys [active_license] :as acct}]
     ;; TODO: Quick hack
     [:div.has-text-right
      (if (and (= role "member") (nil? active_license))
        (str (name role) " (inactive)")
        (name role))])))


(defmethod columns :all [_ query-params]
  [{:title     "Name"
    :dataIndex :name
    :render    render-name}
   {:title     "Email"
    :dataIndex :email
    :render    render-email}
   {:title     "Phone"
    :dataIndex :phone
    :render    (table/maybe-render format/phone-number)}
   {:title     (table/sort-col-title query-params :created "Created" db/params->route)
    :dataIndex :created
    :render    format/date-time-short}
   {:title     "Role"
    :dataIndex :role
    :render    render-role}])


(defn accounts-search []
  (let [is-loading   (subscribe [:ui/loading? :accounts/query])
        search-query (:q @(subscribe [:accounts.list/query-params]))]
    [ant/form-item
     [ant/input-search
      {:placeholder   "Search by name or email"
       :style         {:width "100%"}
       :on-change     #(dispatch [:accounts.list/search-accounts (.. % -target -value)])
       :default-value search-query
       :prefix        (when @is-loading (r/as-element [ant/icon {:type "loading"}]))}]]))


(defn accounts-table []
  (let [selected   (subscribe [:accounts.list/selected-view])
        accounts   (subscribe [:accounts/list])
        params     (subscribe [:accounts.list/query-params])
        is-loading (subscribe [:ui/loading? :accounts/query])]
    [:div
     [:div.table-controls
      [:div.columns
       [:div.column.is-one-third
        [accounts-search]]]]
     [ant/spin
      (tb/assoc-when
       {:tip      "Fetching accounts..."
        :spinning @is-loading}
       :delay (when-not (empty? @accounts) 1000))
      [ant/table {:columns    (columns (keyword @selected) @params)
                  :dataSource (map-indexed #(assoc %2 :key %1) @accounts)}]]]))


;; entrypoint ===================================================================


(defmethod content/view :accounts/list [route]
  [:div
   (typography/view-header "People" "Manage members and applicants")
   [role-menu]
   [:div.mt2
    [accounts-table]]])


;; ==============================================================================
;; entry view ===================================================================
;; ==============================================================================


(defn- most-current-license [account]
  (or (tb/find-by (comp #{:active} :status) (:licenses account))
      (first (:licenses account))))


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
        is-loading (subscribe [:ui/loading? :payments/fetch])]
    [ant/card {:class "is-flush"}
     [payments/payments-table @payments @is-loading
      :columns (conj payments/default-columns :add-check :method)]]))


(defn payments-view [account]
  (let [payments  (subscribe [:payments/by-account-id (:id account)])
        modal-key :accounts.entry/add-check-modal]
    [:div.columns
     [:div.column
      [:div.columns
       [:div.column
        [:p.title.is-5 "Payments"]]
       [:div.column.has-text-right
        [payments/add-check-modal modal-key @payments
         :on-submit #(if (= "new" (:payment %))
                       (dispatch [:accounts.entry/add-payment! modal-key (:id account) %])
                       (dispatch [:accounts.entry/add-check! modal-key %]))]
        [ant/button
         {:type     :dashed
          :on-click #(dispatch [:modal/show modal-key])
          :icon     "plus"}
         "Add Check"]]]
      [payments-table account]]]))


;; notes ========================================================================


(defn notes-view [account]
  (let [notes (subscribe [:accounts.entry/notes])]
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
  (let [is-loading (subscribe [:ui/loading? :accounts.entry/reassign!])
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
       :on-click #(dispatch [:accounts.entry/reassign! license-id form])}
      "Reassign"]]))


(defn- reassign-modal [account]
  (let [is-visible    (subscribe [:modal/visible? db/reassign-modal-key])
        units-loading (subscribe [:ui/loading? :property/fetch])
        rate-loading  (subscribe [:ui/loading? :accounts.entry.reassign/fetch-rate])
        units         (subscribe [:property/units (get-in account [:property :id])])
        form          (subscribe [:accounts.entry.reassign/form-data])
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
          :on-change #(dispatch [:accounts.entry.reassign/select-unit % (:term license)])}
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
          :on-change #(dispatch [:accounts.entry.reassign/update :rate %])}])]]))


(defn membership-actions [account]
  [:div
   [reassign-modal account]
   [ant/button
    {:icon     "swap"
     :on-click #(dispatch [:accounts.entry.reassign/show account])}
    "Reassign"]])


(defn- render-status [_ {status :status}]
  [ant/tooltip {:title status}
   [ant/icon {:class (order/status-icon-class (keyword status))
              :type  (order/status-icon (keyword status))}]])


(defn membership-orders-list [account orders]
  [ant/card
   {:title (str (format/make-first-name-possessive (:name account)) "Premium Service Orders")}
   [ant/table
    (let [service-route #(routes/path-for :services.orders/entry :order-id (.-id %))]
      {:columns     [{:title     ""
                      :dataIndex :status
                      :render    (table/wrap-cljs render-status)}
                     {:title     "Service"
                      :dataIndex :name
                      :render    #(r/as-element
                                   [:a {:href                    (service-route %2)
                                        :dangerouslySetInnerHTML {:__html %1}}])}
                     {:title     "Price"
                      :dataIndex :price
                      :render    (table/wrap-cljs #(if (some? %) (format/currency %) "n/a"))}]
       :dataSource  (map-indexed #(assoc %2 :key %1) orders)
       :pagination  {:size              :small
                     :default-page-size 4}
       :show-header false})]])


(defn membership-view [account]
  (let [license   (most-current-license account)
        is-active (= :active (:status license))
        orders    @(subscribe [:account/orders (:id account)])]
    [:div.columns
     [:div.column
      [membership/license-summary license
       (when is-active {:content [membership-actions account]})]]
     [:div.column
      (when is-active [status-bar account])
      (when is-active [membership-orders-list account orders])]]))



(defn- menu-item [role key]
  [ant/menu-item
   {:key key :disabled (not (db/allowed? role key))}
   (string/capitalize key)])


(defn menu [{role :role}]
  (let [selected (subscribe [:accounts.entry/selected-tab])]
    [ant/menu {:mode          :horizontal
               :selected-keys [@selected]
               :on-click      #(dispatch [:accounts.entry/select-tab (aget % "key")])}
     (map
      (partial menu-item role)
      ["membership" "payments" "application" "notes"])]))


;; entrypoint ===================================================================


(defmethod content/view :accounts/entry
  [{{account-id :account-id} :params, path :path}]
  (let [{:keys [email phone] :as account} @(subscribe [:account (tb/str->int account-id)])
        selected                          (subscribe [:accounts.entry/selected-tab])
        is-loading                        (subscribe [:ui/loading? :account/fetch])]
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
