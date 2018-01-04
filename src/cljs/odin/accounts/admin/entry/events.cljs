(ns odin.accounts.admin.entry.events
  (:require [odin.accounts.admin.entry.db :as db]
            [odin.routes :as routes]
            [re-frame.core :refer [reg-event-fx
                                   reg-event-db
                                   path]]
            [toolbelt.core :as tb]
            [odin.utils.formatters :as format]))


(defmethod routes/dispatches :admin.accounts/entry [route]
  (let [account-id (tb/str->int (get-in route [:params :account-id]))]
    [[:account/fetch account-id {:on-success [::on-fetch-account]}]
     [:payments/fetch account-id]
     [:payment-sources/fetch account-id]
     [:admin.accounts.entry/fetch-notes account-id]]))


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
       {:dispatch [:admin.accounts.entry/select-tab (tab-for-role (:role account))]}))))


(reg-event-db
 :admin.accounts.entry/select-tab
 [(path db/path)]
 (fn [db [_ tab]]
   (assoc db :tab tab)))


;; fetch units ==================================================================


(reg-event-fx
 :admin.accounts.entry.approval/fetch-units
 (fn [_ [k property-id]]
   {:dispatch [:loading k true]
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
    :dispatch [:loading k false]}))


;; approve ======================================================================


(reg-event-fx
 :admin.accounts.entry/approve
 [(path db/path)]
 (fn [{db :db} [k application-id {:keys [move-in unit term]}]]
   {:dispatch [:loading k true]
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
    [[:loading k false]
     [:modal/hide :admin.accounts.approval/modal]
     [:account/fetch (get-in response [:data :approve_application :account :id])]]}))


;; check ========================================================================


(reg-event-fx
 :admin.accounts.entry/add-check!
 [(path db/path)]
 (fn [_ [_ k {:keys [payment amount name check-date received-date]}]]
   {:dispatch [:loading k true]
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
      [[:loading k false]
       [:modal/hide k]
       [:payments/fetch account-id]]})))


;; notes ========================================================================


;; create ===============================


(reg-event-db
 :admin.accounts.entry.create-note/update
 [(path db/path)]
 (fn [db [_ k v]]
   (assoc-in db [:create-form k] v)))


(reg-event-fx
 :admin.accounts.entry/create-note!
 [(path db/path)]
 (fn [_ [k account-id {:keys [subject content notify]}]]
   {:dispatch [:loading k true]
    :graphql  {:mutation
               [[:create_note {:params {:account account-id
                                        :subject subject
                                        :content (format/escape-newlines content)
                                        :notify  notify}}
                 [:id]]]
               :on-success [::create-note-success k account-id]
               :on-failure [:graphql/failure k]}}))

(reg-event-fx
 ::create-note-success
 [(path db/path)]
 (fn [_ [_ k account-id response]]
   {:dispatch-n [[:loading k false]
                 [:admin.accounts.entry/fetch-notes account-id]
                 [:admin.accounts.entry.create-note/update :subject ""]
                 [:admin.accounts.entry.create-note/update :content ""]
                 [:admin.accounts.entry.create-note/update :notify true]]}))


;; pagination ===========================


(reg-event-db
 :admin.accounts.entry.notes/change-pagination
 [(path db/path)]
 (fn [db [_ page size]]
   (assoc db :notes-pagination {:page page :size size})))


;; fetch ================================


(reg-event-fx
 :admin.accounts.entry/fetch-notes
 [(path db/path)]
 (fn [_ [k account-id]]
   {:dispatch [:loading k true]
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
   {:dispatch [:loading k false]
    :db       (->> (get-in response [:data :account :notes])
                   (sort-by :created)
                   (reverse)
                   (assoc db :notes))}))


;; update ===============================


(reg-event-db
 :admin.accounts.entry.note/toggle-editing
 [(path db/path)]
 (fn [db [_ note-id]]
   (update-in db [:editing-notes note-id] not)))


(reg-event-fx
 :admin.accounts.entry/update-note!
 [(path db/path)]
 (fn [_ [k note-id {:keys [subject content]}]]
   {:dispatch [:loading k true]
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
   {:dispatch-n [[:loading k false]
                 [:admin.accounts.entry.note/toggle-editing
                  (get-in response [:data :update_note :id])]
                 [:admin.accounts.entry/fetch-notes
                  (get-in response [:data :update_note :account :id])]]}))


;; delete ===============================


(reg-event-fx
 :admin.accounts.entry.note/delete!
 (fn [{db :db} [k note-id]]
   (let [account-id (tb/str->int (get-in db [:route :params :account-id]))]
     {:dispatch [:loading k true]
      :graphql   {:mutation   [[:delete_note {:note note-id}]]
                  :on-success [::delete-note-success k account-id note-id]
                  :on-failure [:graphql/failure k]}})))


(reg-event-fx
 ::delete-note-success
 [(path db/path)]
 (fn [{db :db} [_ k account-id note-id _]]
   {:dispatch-n [[:loading k false]
                 [:admin.accounts.entry/fetch-notes account-id]]
    :db         (update db :notes (fn [notes]
                                    (remove #(= note-id (:id %)) notes)))}))


;; comment ==============================


(reg-event-db
 :admin.accounts.entry.note/toggle-comment-form
 [(path db/path)]
 (fn [db [_ note-id]]
   (update-in db [:commenting-notes note-id :shown] not)))


(reg-event-db
 :admin.accounts.entry.note.comment/update
 [(path db/path)]
 (fn [db [_ note-id text]]
   (assoc-in db [:commenting-notes note-id :text] text)))


(reg-event-fx
 :admin.accounts.entry.note/add-comment!
 [(path db/path)]
 (fn [db [k note-id text]]
   {:dispatch [:loading k true]
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
      [[:loading k false]
       [:admin.accounts.entry.note.comment/update note-id ""]
       [:admin.accounts.entry.note/toggle-comment-form note-id]]

      :db (update db :notes (fn [notes]
                              (map
                               (fn [note]
                                 (if (= (:id note) note-id)
                                   (-> (update note :comments vec)
                                       (update :comments conj comment))
                                   note))
                               notes)))})))
