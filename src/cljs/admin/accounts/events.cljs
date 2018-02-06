(ns admin.accounts.events
  (:require [admin.accounts.db :as db]
            [admin.routes :as routes]
            [clojure.string :as string]
            [iface.modules.graphql :as graphql]
            [iface.utils.formatters :as format]
            [iface.utils.norms :as norms]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [toolbelt.core :as tb]))


;; ==============================================================================
;; generic ======================================================================
;; ==============================================================================


(defn- parse-query-params
  [{:keys [roles q]}]
  (tb/assoc-when
   {}
   :roles (when-some [rs roles] (vec rs))
   :q q))


(reg-event-fx
 :accounts/query
 [(path db/path)]
 (fn [{db :db} [k params]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:accounts {:params (parse-query-params params)}
                 [:id :name :email :phone :role :created
                  [:property [:id :name]]
                  [:application [:id :move_in :created :updated :submitted :status
                                 [:communities [:id :name]]]]
                  [:active_license [:id :rate :starts :ends :term :status :rent_status
                                    [:unit [:id :code :number]]]]
                  [:deposit [:id :amount :due :amount_remaining :amount_paid :amount_pending]]]]]
               :on-success [::accounts-query k params]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::accounts-query
 [(path db/path)]
 (fn [{db :db} [_ k params response]]
   {:db       (->> (get-in response [:data :accounts])
                   (norms/normalize db :accounts/norms))
    :dispatch [:ui/loading k false]}))


(reg-event-fx
 :account/fetch
 [(path db/path)]
 (fn [{db :db} [k account-id opts]]
   (let [license-selectors [:id :rate :starts :ends :term :status :rent_status
                            [:property [:id :cover_image_url :name]]

                            [:unit [:id :code :number]]]]
     {:dispatch [:ui/loading k true]
      :graphql   {:query
                  [[:account {:id account-id}
                    [:id :name :email :phone :role :dob
                     [:deposit [:amount :due :status]]
                     [:property [:id :name]]
                     ;; TODO: Move to separate query
                     [:application [:id :move_in :created :updated :submitted :status :term :has_pet
                                    :approved_at
                                    [:approved_by [:id :name]]
                                    [:communities [:id :name]]
                                    [:fitness [:experience :skills :free_time :interested :dealbreakers :conflicts]]
                                    [:income [:id :uri :name]]
                                    [:pet [:type :breed :weight :sterile :vaccines :bitten :demeanor :daytime_care]]]]
                     [:active_license license-selectors]
                     ;; TODO: Move to separate query
                     [:licenses license-selectors]]]]
                  :on-success [::account-fetch k opts]
                  :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::account-fetch
 [(path db/path)]
 (fn [{db :db} [_ k opts response]]
   (let [account (get-in response [:data :account])]
     {:db         (norms/assoc-norm db :accounts/norms (:id account) account)
      :dispatch-n (tb/conj-when
                   [[:ui/loading k false]]
                   (when-let [ev (:on-success opts)] (conj ev account)))})))


;; ==============================================================================
;; list view ====================================================================
;; ==============================================================================


(defmethod routes/dispatches :accounts/list [{params :params}]
  (if (empty? params)
    [[:accounts.list/set-default-route]]
    [[:accounts.list/fetch (db/parse-query-params params)]]))


(reg-event-fx
 :accounts.list/set-default-route
 [(path db/path)]
 (fn [{db :db} _]
   {:route (db/params->route (:params db))}))


(defn- accounts-query-params [{:keys [selected-view q] :as query-params}]
  (let [roles (when-not (= "all" selected-view)
                [(keyword selected-view)])]
    (tb/assoc-when {:q q} :roles roles)))


(reg-event-fx
 :accounts.list/fetch
 [(path db/path)]
 (fn [{db :db} [k query-params]]
   {:dispatch [:accounts/query (accounts-query-params query-params)]
    :db       (assoc db :params query-params)}))


(reg-event-fx
 :accounts.list/select-view
 [(path db/path)]
 (fn [{db :db} [_ view]]
   {:route (-> (:params db)
               (assoc :selected-view view)
               (merge (db/default-sort-params view))
               (db/params->route))}))


(reg-event-fx
 :accounts.list/search-accounts
 [(path db/path)]
 (fn [{db :db} [k q]]
   {:dispatch-throttle {:id              k
                        :window-duration 500
                        :leading?        false
                        :trailing?       true
                        :dispatch        [::search-accounts q]}}))


(reg-event-fx
 ::search-accounts
 [(path db/path)]
 (fn [{db :db} [_ query]]
   {:route (db/params->route (assoc (:params db) :q query))}))

;; ==============================================================================
;; entry ========================================================================
;; ==============================================================================


(defmethod routes/dispatches :accounts/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [[:account/fetch account-id {:on-success [::on-fetch-account]}]
     [:payments/fetch account-id]
     [:payment-sources/fetch account-id]
     [:accounts.entry/fetch-notes account-id]]))


(defn- tab-for-role [role]
  (case role
    :member    "membership"
    :applicant "application"
    "notes"))


(reg-event-fx
 ::on-fetch-account
 [(path db/path)]
 (fn [{db :db} [_ account]]
   (let [current (:tab db)]
     (when (or (nil? current) (not (db/allowed? (:role account) current)))
       {:dispatch [:accounts.entry/select-tab (tab-for-role (:role account))]}))))


(reg-event-db
 :accounts.entry/select-tab
 [(path db/path)]
 (fn [db [_ tab]]
   (assoc db :tab tab)))


;; fetch units ==================================================================


(reg-event-fx
 :accounts.entry.approval/fetch-units
 (fn [_ [k property-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:units {:params {:property (tb/str->int property-id)}}
                 [:id :code :number
                  [:occupant [:name
                              [:active_license [:ends]]]]]]]
               :on-success [::fetch-units-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-units-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:db       (assoc db :units (get-in response [:data :units]))
    :dispatch [:ui/loading k false]}))


;; approve ======================================================================


(reg-event-fx
 :accounts.entry/approve
 [(path db/path)]
 (fn [{db :db} [k application-id {:keys [move-in unit term]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:approve_application {:application application-id
                                       :params      {:move_in (.toISOString move-in)
                                                     :unit    (tb/str->int unit)
                                                     :term    (tb/str->int term)}}
                 [:id [:account [:id]]]]]
               :on-success [::approve-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::approve-success
 [(path db/path)]
 (fn [_ [_ k response]]
   {:dispatch-n
    [[:ui/loading k false]
     [:modal/hide :accounts.approval/modal]
     [:account/fetch (get-in response [:data :approve_application :account :id])]]}))


;; reassign =====================================================================


(reg-event-fx
 :accounts.entry.reassign/show
 [(path db/path)]
 (fn [_ [_ account]]
   {:dispatch-n [[:modal/show db/reassign-modal-key]
                 [:property/fetch (get-in account [:property :id])]]}))


(reg-event-db
 :accounts.entry.reassign/update
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:reassign-form k] v)))


(reg-event-fx
 :accounts.entry.reassign/select-unit
 [(path db/path)]
 (fn [db [_ unit term]]
   (let [unit (tb/str->int unit)]
     {:dispatch-n [[:accounts.entry.reassign/update :unit unit]
                   [:accounts.entry.reassign/fetch-rate unit term]]})))


(reg-event-fx
 :accounts.entry.reassign/fetch-rate
 [(path db/path)]
 (fn [_ [k unit-id term]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:unit_rate {:unit unit-id
                             :term term}
                 [:rate]]]
               :on-success [::fetch-rate-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-rate-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (let [rate (get-in response [:data :unit_rate :rate])]
     {:dispatch-n [[:ui/loading k false]
                   [:accounts.entry.reassign/update :rate rate]]})))


(reg-event-fx
 :accounts.entry/reassign!
 [(path db/path)]
 (fn [_ [k license-id {:keys [unit rate]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:reassign_member_unit {:params {:license license-id
                                                 :unit    unit
                                                 :rate    rate}}
                 [:id [:account [:id]]]]]
               :on-success [::reassign-unit-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::reassign-unit-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (let [account-id (get-in response [:data :reassign_member_unit :account :id])]
     {:dispatch-n [[:ui/loading k false]
                   [:modal/hide db/reassign-modal-key]
                   [:payment-sources/fetch account-id]
                   [:account/fetch account-id]]})))


;; payment ======================================================================


(reg-event-fx
 :accounts.entry/add-payment!
 [(path db/path)]
 (fn [_ [_ k account-id {:keys [type month amount] :as params}]]
   {:dispatch [:ui/loading k :true]
    :graphql  {:mutation
               [[:create_payment {:params {:account account-id
                                           :type    (keyword type)
                                           :month   month
                                           :amount  (float amount)}}
                 [:id]]]
               :on-success [::add-payment-success k params]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::add-payment-success
 [(path db/path)]
 (fn [_ [_ k params response]]
   (let [payment-id (get-in response [:data :create_payment :id])]
     {:dispatch [:accounts.entry/add-check! k (assoc params :payment payment-id)]})))


;; check ========================================================================


(reg-event-fx
 :accounts.entry/add-check!
 [(path db/path)]
 (fn [_ [_ k {:keys [payment amount name check-date received-date]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:create_check {:params {:payment       payment
                                         :amount        (float amount)
                                         :name          name
                                         :received_date received-date
                                         :check_date    check-date}}
                 [[:payment [[:account [:id]]]]]]]
               :on-success [::add-check-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::add-check-success
 [(path db/path)]
 (fn [_ [_ k response]]
   (let [account-id (get-in response [:data :create_check :payment :account :id])]
     {:dispatch-n
      [[:ui/loading k false]
       [:modal/hide k]
       [:payments/fetch account-id]]})))


;; notes ========================================================================


;; create ===============================


(reg-event-db
 :accounts.entry.create-note/toggle
 [(path db/path)]
 (fn [db _]
   (update db :showing-create-note not)))


(reg-event-db
 :accounts.entry.create-note/update
 [(path db/path)]
 (fn [db [_ account-id k v]]
   (assoc-in db [:create-form account-id k] v)))


(reg-event-db
 :accounts.entry.create-note/clear
 [(path db/path)]
 (fn [db [_ account-id]]
   (-> (assoc-in db [:create-form account-id :subject] "")
       (assoc-in [:create-form account-id :content] "")
       (assoc-in [:create-form account-id :notify] true))))


(reg-event-fx
 :accounts.entry/create-note!
 [(path db/path)]
 (fn [_ [k account-id {:keys [subject content notify]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:create_note {:params {:account account-id
                                        :subject subject
                                        :content (-> (format/escape-newlines content)
                                                     (string/replace #"\"" "&quot;")
                                                     (string/replace #"'" "&#39;"))
                                        :notify  notify}}
                 [:id]]]
               :on-success [::create-note-success k account-id]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::create-note-success
 [(path db/path)]
 (fn [_ [_ k account-id response]]
   {:dispatch-n
    [[:ui/loading k false]
     [:accounts.entry/fetch-notes account-id]
     [:accounts.entry.create-note/toggle]
     [:accounts.entry.create-note/clear account-id]]}))


;; pagination ===========================


(reg-event-db
 :accounts.entry.notes/change-pagination
 [(path db/path)]
 (fn [db [_ page size]]
   (assoc db :notes-pagination {:page page :size size})))


;; fetch ================================


(reg-event-fx
 :accounts.entry/fetch-notes
 [(path db/path)]
 (fn [_ [k account-id]]
   {:dispatch [:ui/loading k true]
    :graphql  {:query
               [[:account {:id account-id}
                 [:id [:notes [:id :subject :content :created :updated
                               [:comments [:id :subject :content :created :updated
                                           [:author [:id :name]]]]
                               [:author [:id :name]]]]]]]
               :on-success [::fetch-notes-success k]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::fetch-notes-success
 [(path db/path)]
 (fn [{db :db} [_ k response]]
   {:dispatch [:ui/loading k false]
    :db       (->> (get-in response [:data :account :notes])
                   (sort-by :created)
                   (reverse)
                   (assoc db :notes))}))


;; update ===============================


(reg-event-db
 :accounts.entry.note/toggle-editing
 [(path db/path)]
 (fn [db [_ note-id]]
   (update-in db [:editing-notes note-id] not)))


(reg-event-fx
 :accounts.entry/update-note!
 [(path db/path)]
 (fn [_ [k note-id {:keys [subject content]}]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:update_note {:params {:note    note-id
                                        :subject subject
                                        :content (format/escape-newlines content)}}
                 [:id [:account [:id]]]]]
               :on-success [::update-note-success k]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::update-note-success
 [(path db/path)]
 (fn [_ [_ k response]]
   {:dispatch-n [[:ui/loading k false]
                 [:accounts.entry.note/toggle-editing
                  (get-in response [:data :update_note :id])]
                 [:accounts.entry/fetch-notes
                  (get-in response [:data :update_note :account :id])]]}))


;; delete ===============================


(reg-event-fx
 :accounts.entry.note/delete!
 (fn [{db :db} [k note-id]]
   (let [account-id (tb/str->int (get-in db [:route :params :account-id]))]
     {:dispatch [:ui/loading k true]
      :graphql   {:mutation   [[:delete_note {:note note-id}]]
                  :on-success [::delete-note-success k account-id note-id]
                  :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::delete-note-success
 [(path db/path)]
 (fn [{db :db} [_ k account-id note-id _]]
   {:dispatch-n [[:ui/loading k false]
                 [:accounts.entry/fetch-notes account-id]]
    :db         (update db :notes (fn [notes]
                                    (remove #(= note-id (:id %)) notes)))}))


;; comment ==============================


(reg-event-db
 :accounts.entry.note/toggle-comment-form
 [(path db/path)]
 (fn [db [_ note-id]]
   (update-in db [:commenting-notes note-id :shown] not)))


(reg-event-db
 :accounts.entry.note.comment/update
 [(path db/path)]
 (fn [db [_ note-id text]]
   (assoc-in db [:commenting-notes note-id :text] text)))


(reg-event-fx
 :accounts.entry.note/add-comment!
 [(path db/path)]
 (fn [db [k note-id text]]
   {:dispatch [:ui/loading k true]
    :graphql  {:mutation
               [[:add_note_comment {:note note-id
                                    :text text}
                 [:id :subject :content :created :updated
                  [:author [:id :name]]]]]
               :on-success [::add-note-comment-success k note-id]
               :on-failure [:graphql/failure k]}}))


(reg-event-fx
 ::add-note-comment-success
 [(path db/path)]
 (fn [{db :db} [_ k note-id response]]
   (let [comment (get-in response [:data :add_note_comment])]
     {:dispatch-n
      [[:ui/loading k false]
       [:accounts.entry.note.comment/update note-id ""]
       [:accounts.entry.note/toggle-comment-form note-id]]

      :db (update db :notes (fn [notes]
                              (map
                               (fn [note]
                                 (if (= (:id note) note-id)
                                   (-> (update note :comments vec)
                                       (update :comments conj comment))
                                   note))
                               notes)))})))
